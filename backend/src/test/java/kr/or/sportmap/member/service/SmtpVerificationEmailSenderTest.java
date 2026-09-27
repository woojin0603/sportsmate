package kr.or.sportmap.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import kr.or.sportmap.member.mail.SmtpVerificationEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

class SmtpVerificationEmailSenderTest {

  @Test
  @SuppressWarnings("unchecked")
  void rejectsMissingSmtpConfiguration() {
    var sender = new SmtpVerificationEmailSender(
      mock(ObjectProvider.class),
      "",
      ""
    );
    var error = assertThrows(ResponseStatusException.class, () ->
      sender.send("demo@example.com", "https://example.com/confirm")
    );
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
  }

  @Test
  @SuppressWarnings("unchecked")
  void buildsEmailForArbitraryRecipientThroughSmtpAdapter() throws Exception {
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    JavaMailSender transport = mock(JavaMailSender.class);
    when(provider.getIfAvailable()).thenReturn(transport);
    MimeMessage message = new MimeMessage(
      Session.getInstance(new Properties())
    );
    when(transport.createMimeMessage()).thenReturn(message);
    var sender = new SmtpVerificationEmailSender(
      provider,
      "smtp.example.com",
      "noreply@example.com"
    );
    sender.send(
      "recipient@other.example",
      "https://example.com/api/users/email/confirm?token=test"
    );
    assertEquals(
      "recipient@other.example",
      message.getAllRecipients()[0].toString()
    );
    assertEquals("[SportMap] 회원가입 이메일 인증", message.getSubject());
    assertNotNull(message.getContent());
    verify(transport).send(message);
  }

  @Test
  @SuppressWarnings("unchecked")
  void providerFailureIsNotReportedAsSuccess() {
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    JavaMailSender transport = mock(JavaMailSender.class);
    when(provider.getIfAvailable()).thenReturn(transport);
    when(transport.createMimeMessage()).thenReturn(
      new MimeMessage(Session.getInstance(new Properties()))
    );
    doThrow(new MailSendException("private provider detail"))
      .when(transport)
      .send(any(MimeMessage.class));
    var sender = new SmtpVerificationEmailSender(
      provider,
      "smtp.example.com",
      "noreply@example.com"
    );
    var error = assertThrows(ResponseStatusException.class, () ->
      sender.send("demo@example.com", "https://example.com/confirm")
    );
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    assertFalse(error.getReason().contains("private provider detail"));
  }
}
