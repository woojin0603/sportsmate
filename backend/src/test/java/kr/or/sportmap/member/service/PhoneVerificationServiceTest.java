package kr.or.sportmap.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.domain.PhoneVerification;
import kr.or.sportmap.member.repository.*;
import kr.or.sportmap.member.sms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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
    "app.sms.mode=mock",
    "server.address=127.0.0.1",
    "app.sms.daily-limit=100",
  },
  showSql = false
)
@ActiveProfiles("test")
@Import({
  PhoneVerificationService.class,
  SmsGateway.class,
  SmsDeliveryConfiguration.class,
  MemberService.class,
  PhoneVerificationServiceTest.Passwords.class,
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PhoneVerificationServiceTest {

  @TestConfiguration
  static class Passwords {

    @Bean
    PasswordEncoder passwords() {
      return new BCryptPasswordEncoder(4);
    }
  }

  @Autowired
  PhoneVerificationService service;

  @Autowired
  PhoneVerificationRepository repository;

  @Autowired
  SmsBudgetRepository budgets;

  @Autowired
  MemberService members;

  @Autowired
  MemberRepository memberRepository;

  @MockBean
  EmailVerificationService emails;

  @MockBean
  JwtEncoder jwtEncoder;

  @org.springframework.boot.test.mock.mockito.SpyBean
  SmsGateway gateway;

  @BeforeEach
  void reset() {
    repository.deleteAll();
    var budget = budgets.findById(1L).orElseThrow();
    budget.day = null;
    budget.sentCount = 0;
    budgets.saveAndFlush(budget);
  }

  private PhoneVerification row() {
    return repository.findAll().get(0);
  }

  private ResponseStatusException rejected(Runnable action) {
    return assertThrows(ResponseStatusException.class, action::run);
  }

  private PhoneVerificationService.SendResult send() {
    return service.send("010-1234-5678", "127.0.0.1");
  }

  @Test
  void verifiesNormalizedNumberAndConsumesOnlyOnce() {
    var sent = send();
    assertTrue(sent.mock());
    assertTrue(sent.developmentCode().matches("[0-9]{6}"));
    assertNotEquals(sent.developmentCode(), row().codeHash);
    assertNotEquals(sent.requestToken(), row().id);
    var verified = service.verify(
      "01012345678",
      sent.requestToken(),
      sent.developmentCode()
    );
    assertNull(row().codeHash);
    assertNotEquals(verified.verificationToken(), row().tokenHash);
    service.consume(
      "01012345678",
      sent.requestToken(),
      verified.verificationToken()
    );
    rejected(() ->
      service.consume(
        "01012345678",
        sent.requestToken(),
        verified.verificationToken()
      )
    );
  }

  @Test
  void requiresCorrectPhoneAndRequestAndSignupToken() {
    var sent = send();
    rejected(() ->
      service.verify("01099999999", sent.requestToken(), sent.developmentCode())
    );
    rejected(() ->
      service.verify("01012345678", "invented", sent.developmentCode())
    );
    rejected(() ->
      service.consume("01012345678", sent.requestToken(), "invented")
    );
    var verified = service.verify(
      "01012345678",
      sent.requestToken(),
      sent.developmentCode()
    );
    rejected(() ->
      service.consume(
        "01099999999",
        sent.requestToken(),
        verified.verificationToken()
      )
    );
    rejected(() ->
      service.consume("01012345678", sent.requestToken(), "wrong")
    );
    service.consume(
      "01012345678",
      sent.requestToken(),
      verified.verificationToken()
    );
  }

  @Test
  void wrongAttemptsPersistAcrossTransactionsAndLockAfterFive() {
    var sent = send();
    String wrong = sent.developmentCode().equals("000000")
      ? "111111"
      : "000000";
    for (int i = 1; i <= 5; i++) {
      rejected(() -> service.verify("01012345678", sent.requestToken(), wrong));
      assertEquals(i, row().failedAttempts);
    }
    rejected(() ->
      service.verify("01012345678", sent.requestToken(), sent.developmentCode())
    );
  }

  @Test
  void expiredCodeIsRejected() {
    var sent = send();
    var row = row();
    row.expiresAt = Instant.now().minusSeconds(1);
    repository.saveAndFlush(row);
    rejected(() ->
      service.verify("01012345678", sent.requestToken(), sent.developmentCode())
    );
  }

  @Test
  void expiredSignupProofIsRejected() {
    var sent = send();
    var proof = service.verify(
      "01012345678",
      sent.requestToken(),
      sent.developmentCode()
    );
    var row = row();
    row.expiresAt = Instant.now().minusSeconds(1);
    repository.saveAndFlush(row);
    rejected(() ->
      service.consume(
        "01012345678",
        sent.requestToken(),
        proof.verificationToken()
      )
    );
  }

  @Test
  void resendHasCooldownAndInvalidatesPreviousProof() {
    var sent = send();
    var proof = service.verify(
      "01012345678",
      sent.requestToken(),
      sent.developmentCode()
    );
    assertEquals(
      HttpStatus.TOO_MANY_REQUESTS,
      rejected(this::send).getStatusCode()
    );
    var row = row();
    row.sentAt = Instant.now().minusSeconds(61);
    repository.saveAndFlush(row);
    var next = send();
    rejected(() ->
      service.consume(
        "01012345678",
        sent.requestToken(),
        proof.verificationToken()
      )
    );
    service.verify("01012345678", next.requestToken(), next.developmentCode());
  }

  @Test
  void capsPhoneAtFiveRequestsPerDay() {
    for (int i = 0; i < 5; i++) {
      send();
      for (var row : repository.findAll()) {
        row.sentAt = Instant.now().minusSeconds(61);
        repository.saveAndFlush(row);
      }
    }
    assertEquals(
      HttpStatus.TOO_MANY_REQUESTS,
      rejected(this::send).getStatusCode()
    );
  }

  @Test
  void capsClientAndGlobalSends() {
    for (int i = 0; i < 10; i++) service.send(
      "0101234568" + i,
      "shared-client"
    );
    assertEquals(
      HttpStatus.TOO_MANY_REQUESTS,
      rejected(() ->
        service.send("01099999999", "shared-client")
      ).getStatusCode()
    );
    var budget = budgets.findById(1L).orElseThrow();
    budget.sentCount = 100;
    budgets.saveAndFlush(budget);
    assertEquals(
      HttpStatus.TOO_MANY_REQUESTS,
      rejected(() ->
        service.send("01099999999", "other-client")
      ).getStatusCode()
    );
  }

  @Test
  void concurrentConsumptionSucceedsOnlyOnce() throws Exception {
    var sent = send();
    var proof = service.verify(
      "01012345678",
      sent.requestToken(),
      sent.developmentCode()
    );
    var executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> consume = () -> {
        try {
          service.consume(
            "01012345678",
            sent.requestToken(),
            proof.verificationToken()
          );
          return true;
        } catch (ResponseStatusException rejected) {
          return false;
        }
      };
      var results = executor.invokeAll(List.of(consume, consume));
      assertEquals(
        1,
        (results.get(0).get() ? 1 : 0) + (results.get(1).get() ? 1 : 0)
      );
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void mockProofCannotBeUsedAfterSwitchToRealDelivery() {
    var sent = send();
    var proof = service.verify(
      "01012345678",
      sent.requestToken(),
      sent.developmentCode()
    );
    var row = row();
    row.mockDelivery = false;
    repository.saveAndFlush(row);
    rejected(() ->
      service.consume(
        "01012345678",
        sent.requestToken(),
        proof.verificationToken()
      )
    );
  }

  @Test
  void signupRequiresPhoneProofAndRollsBackConsumptionOnEmailFailure() {
    String username = "phone_test_user";
    var sent = send();
    var proof = service.verify(
      "01012345678",
      sent.requestToken(),
      sent.developmentCode()
    );
    rejected(() ->
      members.signup(
        "테스트",
        username,
        "Example123!",
        LocalDate.of(2000, 1, 1),
        "phone-test@example.com",
        "email-proof",
        "01012345678",
        null,
        null,
        Member.Gender.OTHER
      )
    );
    assertFalse(memberRepository.existsByUsername(username));
    doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST))
      .when(emails)
      .consume(anyString(), anyString());
    rejected(() ->
      members.signup(
        "테스트",
        username,
        "Example123!",
        LocalDate.of(2000, 1, 1),
        "phone-test@example.com",
        "email-proof",
        "01012345678",
        sent.requestToken(),
        proof.verificationToken(),
        Member.Gender.OTHER
      )
    );
    assertFalse(row().consumed);
    doNothing().when(emails).consume(anyString(), anyString());
    var member = members.signup(
      "테스트",
      username,
      "Example123!",
      LocalDate.of(2000, 1, 1),
      "phone-test@example.com",
      "email-proof",
      "01012345678",
      sent.requestToken(),
      proof.verificationToken(),
      Member.Gender.OTHER
    );
    assertTrue(row().consumed);
    memberRepository.deleteById(member.id);
  }

  @Test
  void providerFailurePersistsQuotaAndCannotBeVerified() {
    doThrow(new IllegalStateException("provider unavailable"))
      .when(gateway)
      .send(anyString(), anyString());
    assertEquals(
      HttpStatus.SERVICE_UNAVAILABLE,
      rejected(this::send).getStatusCode()
    );
    assertEquals(1, budgets.findById(1L).orElseThrow().sentCount);
    assertTrue(row().consumed);
    assertNull(row().codeHash);
    assertEquals(
      HttpStatus.TOO_MANY_REQUESTS,
      rejected(this::send).getStatusCode()
    );
  }

  @Test
  void realModeResponseNeverContainsDevelopmentCode() throws Exception {
    doReturn(false).when(gateway).isMock();
    var sent = send();
    assertFalse(sent.mock());
    assertNull(sent.developmentCode());
    var mapper =
      new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    assertFalse(
      mapper.readTree(mapper.writeValueAsString(sent)).has("developmentCode")
    );
  }

  @Test
  void concurrentSendsCannotBypassCooldown() throws Exception {
    var executor = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> dispatch = () -> {
        try {
          send();
          return true;
        } catch (ResponseStatusException rejected) {
          return false;
        }
      };
      var results = executor.invokeAll(List.of(dispatch, dispatch));
      assertEquals(
        1,
        (results.get(0).get() ? 1 : 0) + (results.get(1).get() ? 1 : 0)
      );
      assertEquals(1, repository.count());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void invalidPhoneNumbersAreRejected() {
    for (String phone : List.of(
      "0212345678",
      "00000000000",
      "010abc12345678",
      "+821012345678"
    )) {
      rejected(() -> service.send(phone, "client"));
    }
    assertEquals(0, repository.count());
  }

  @Test
  void mockConfigurationFailsClosed() {
    var senders = Map.<String, SmsSender>of(
      "mockSmsSender",
      (phone, code) -> {}
    );
    var environment = new MockEnvironment().withProperty(
      "server.address",
      "127.0.0.1"
    );
    environment.setActiveProfiles("production");
    assertThrows(IllegalStateException.class, () ->
      new SmsGateway(senders, "mock", environment)
    );
    environment.setActiveProfiles("local", "production");
    assertThrows(IllegalStateException.class, () ->
      new SmsGateway(senders, "mock", environment)
    );
    environment.setActiveProfiles("local");
    environment.setProperty("server.address", "0.0.0.0");
    assertThrows(IllegalStateException.class, () ->
      new SmsGateway(senders, "mock", environment)
    );
    assertThrows(IllegalStateException.class, () ->
      new SmsGateway(senders, "live", environment)
    );
  }
}
