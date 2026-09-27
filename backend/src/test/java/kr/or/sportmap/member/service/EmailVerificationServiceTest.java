package kr.or.sportmap.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.mail.*;
import kr.or.sportmap.member.repository.*;
import kr.or.sportmap.member.sms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@DataJpaTest(
  properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.mail.mode=mock",
    "app.sms.mode=mock",
    "server.address=127.0.0.1",
  },
  showSql = false
)
@ActiveProfiles("test")
@Import({
  EmailVerificationService.class,
  EmailGateway.class,
  EmailDeliveryConfiguration.class,
  SmtpVerificationEmailSender.class,
  PhoneVerificationService.class,
  SmsGateway.class,
  SmsDeliveryConfiguration.class,
  MemberService.class,
  EmailVerificationServiceTest.Passwords.class,
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailVerificationServiceTest {

  @TestConfiguration
  static class Passwords {

    @Bean
    PasswordEncoder passwords() {
      return new BCryptPasswordEncoder(4);
    }
  }

  @Autowired
  EmailVerificationService service;

  @Autowired
  EmailVerificationRepository repository;

  @Autowired
  PhoneVerificationService phones;

  @Autowired
  PhoneVerificationRepository phoneRepository;

  @Autowired
  SmsBudgetRepository budgets;

  @Autowired
  MemberService members;

  @Autowired
  MemberRepository memberRepository;

  @SpyBean
  EmailGateway gateway;

  @MockBean
  JavaMailSender smtp;

  @MockBean
  JwtEncoder jwtEncoder;

  @BeforeEach
  void clean() {
    repository.deleteAll();
    phoneRepository.deleteAll();
    var budget = budgets.findById(1L).orElseThrow();
    budget.day = null;
    budget.sentCount = 0;
    budgets.saveAndFlush(budget);
  }

  private String linkToken(EmailVerificationService.SendResult sent) {
    return sent.developmentConfirmationUrl().split("token=")[1];
  }

  private EmailVerificationService.SendResult send() {
    return service.sendLink("demo@example.com");
  }

  private ResponseStatusException rejected(Runnable action) {
    return assertThrows(ResponseStatusException.class, action::run);
  }

  @Test
  void mockUsesNormalConfirmationFlowWithoutSmtp() {
    var sent = service.sendLink(" Demo@Example.com ");
    assertTrue(sent.mock());
    assertTrue(
      sent
        .developmentConfirmationUrl()
        .startsWith("/api/users/email/confirm?token=")
    );
    assertFalse(
      service.status("demo@example.com", sent.requestToken()).verified()
    );
    rejected(() -> service.consume("demo@example.com", sent.requestToken()));
    service.confirm(linkToken(sent));
    var status = service.status("demo@example.com", sent.requestToken());
    assertTrue(status.verified());
    assertNotEquals(
      status.verificationToken(),
      repository.findAll().get(0).tokenHash
    );
    service.consume("demo@example.com", status.verificationToken());
    assertEquals(0, repository.count());
    rejected(() ->
      service.consume("demo@example.com", status.verificationToken())
    );
    verifyNoInteractions(smtp);
  }

  @Test
  void repeatedStatusChecksReturnSameProof() {
    var sent = send();
    service.confirm(linkToken(sent));
    var first = service.status("demo@example.com", sent.requestToken());
    var second = service.status("demo@example.com", sent.requestToken());
    assertEquals(first.verificationToken(), second.verificationToken());
    service.consume("demo@example.com", first.verificationToken());
  }

  @Test
  void wrongAddressAndTokenCannotVerifyOrSignup() {
    var sent = send();
    rejected(() -> service.confirm("wrong"));
    rejected(() -> service.status("other@example.com", sent.requestToken()));
    rejected(() -> service.status("demo@example.com", "wrong"));
    service.confirm(linkToken(sent));
    var proof = service.status("demo@example.com", sent.requestToken());
    rejected(() ->
      service.consume("other@example.com", proof.verificationToken())
    );
    rejected(() -> service.consume("demo@example.com", "wrong"));
  }

  @Test
  void linkAndProofExpire() {
    var sent = send();
    var row = repository.findAll().get(0);
    row.expiresAt = Instant.now().minusSeconds(1);
    repository.saveAndFlush(row);
    rejected(() -> service.confirm(linkToken(sent)));
    rejected(() -> service.status("demo@example.com", sent.requestToken()));
    rejected(() -> service.consume("demo@example.com", "anything"));
  }

  @Test
  void verifiedProofAlsoExpires() {
    var sent = send();
    service.confirm(linkToken(sent));
    var proof = service.status("demo@example.com", sent.requestToken());
    var row = repository.findAll().get(0);
    row.expiresAt = Instant.now().minusSeconds(1);
    repository.saveAndFlush(row);
    rejected(() ->
      service.consume("demo@example.com", proof.verificationToken())
    );
  }

  @Test
  void resendIsLimitedAndInvalidatesOldLinkAndProof() {
    var first = send();
    assertEquals(
      HttpStatus.TOO_MANY_REQUESTS,
      rejected(this::send).getStatusCode()
    );
    service.confirm(linkToken(first));
    var proof = service.status("demo@example.com", first.requestToken());
    var row = repository.findAll().get(0);
    row.sentAt = Instant.now().minusSeconds(61);
    repository.saveAndFlush(row);
    var next = send();
    rejected(() -> service.confirm(linkToken(first)));
    rejected(() -> service.status("demo@example.com", first.requestToken()));
    rejected(() ->
      service.consume("demo@example.com", proof.verificationToken())
    );
    service.confirm(linkToken(next));
  }

  @Test
  void modeChangeRejectsMockConfirmationAndProof() {
    var sent = send();
    doReturn(false).when(gateway).isMock();
    rejected(() -> service.confirm(linkToken(sent)));
    rejected(() -> service.status("demo@example.com", sent.requestToken()));
    doReturn(true).when(gateway).isMock();
    service.confirm(linkToken(sent));
    var proof = service.status("demo@example.com", sent.requestToken());
    doReturn(false).when(gateway).isMock();
    rejected(() ->
      service.consume("demo@example.com", proof.verificationToken())
    );
  }

  @Test
  void legacyRecordsRequireReverification() {
    var sent = send();
    var row = repository.findAll().get(0);
    row.mockDelivery = null;
    repository.saveAndFlush(row);
    rejected(() -> service.confirm(linkToken(sent)));
  }

  @Test
  void realModeNeverReturnsDevelopmentLink() throws Exception {
    doReturn(false).when(gateway).isMock();
    var sent = send();
    assertFalse(sent.mock());
    assertNull(sent.developmentConfirmationUrl());
    var mapper =
      new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    assertFalse(
      mapper
        .readTree(mapper.writeValueAsString(sent))
        .has("developmentConfirmationUrl")
    );
  }

  @Test
  void deliveryFailureDoesNotCreateValidChallenge() {
    doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
      .when(gateway)
      .send(anyString(), anyString());
    assertEquals(
      HttpStatus.SERVICE_UNAVAILABLE,
      rejected(this::send).getStatusCode()
    );
    assertEquals(0, repository.count());
  }

  @Test
  void concurrentConfirmationIsSingleUse() throws Exception {
    var sent = send();
    var executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> confirm = () -> {
        try {
          service.confirm(linkToken(sent));
          return true;
        } catch (ResponseStatusException rejected) {
          return false;
        }
      };
      var results = executor.invokeAll(List.of(confirm, confirm));
      assertEquals(
        1,
        (results.get(0).get() ? 1 : 0) + (results.get(1).get() ? 1 : 0)
      );
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void completeSignupNeedsBothProofsAndCanLoginWithoutSendingAnything() {
    var email = send();
    service.confirm(linkToken(email));
    var emailProof = service.status("demo@example.com", email.requestToken());
    rejected(() ->
      members.signup(
        "시연",
        "email_demo",
        "Example123!",
        LocalDate.of(2000, 1, 1),
        "demo@example.com",
        emailProof.verificationToken(),
        "01012345678",
        null,
        null,
        Member.Gender.OTHER
      )
    );
    var phone = phones.send("01012345678", "local-test");
    var phoneProof = phones.verify(
      "01012345678",
      phone.requestToken(),
      phone.developmentCode()
    );
    var member = members.signup(
      "시연",
      "email_demo",
      "Example123!",
      LocalDate.of(2000, 1, 1),
      "demo@example.com",
      emailProof.verificationToken(),
      "01012345678",
      phone.requestToken(),
      phoneProof.verificationToken(),
      Member.Gender.OTHER
    );
    assertEquals(member.id, members.login("email_demo", "Example123!").id);
    assertEquals(HttpStatus.CONFLICT, rejected(this::send).getStatusCode());
    assertEquals(0, repository.count());
    verifyNoInteractions(smtp);
    memberRepository.deleteById(member.id);
  }

  @Test
  void unsafeMockConfigurationFailsClosed() {
    var senders = Map.<String, VerificationEmailSender>of(
      "mockVerificationEmailSender",
      (email, url) -> {}
    );
    var env = new MockEnvironment().withProperty("server.address", "127.0.0.1");
    for (String[] profiles : List.of(
      new String[] { "production" },
      new String[] { "local", "production" }
    )) {
      env.setActiveProfiles(profiles);
      assertThrows(IllegalStateException.class, () ->
        new EmailGateway(senders, "mock", "", env)
      );
    }
    env.setActiveProfiles("local");
    env.setProperty("server.address", "0.0.0.0");
    assertThrows(IllegalStateException.class, () ->
      new EmailGateway(senders, "mock", "", env)
    );
  }

  @Test
  void liveLinksUseConfiguredHttpsOrigin() {
    var senders = Map.<String, VerificationEmailSender>of(
      "liveVerificationEmailSender",
      (email, url) -> {}
    );
    var env = new MockEnvironment();
    for (String base : List.of(
      "",
      "http://example.com",
      "https://example.com/?bad=1",
      "https://user@example.com",
      "https://example.com/path"
    )) {
      assertThrows(IllegalStateException.class, () ->
        new EmailGateway(senders, "live", base, env)
      );
    }
    var live = new EmailGateway(senders, "live", "https://example.com/", env);
    assertEquals(
      "https://example.com/api/users/email/confirm?token=test",
      live.confirmationUrl("test")
    );
  }
}
