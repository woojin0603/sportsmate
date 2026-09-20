package kr.or.sportmap.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import kr.or.sportmap.member.repository.EmailVerificationRepository;
import kr.or.sportmap.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class EmailVerificationServiceTest {

  /** SMTP 설정 없이 발송 성공으로 응답하지 않는다. */
  @Test
  @SuppressWarnings("unchecked")
  void rejectsSendingWithoutMailConfiguration() {
    EmailVerificationService service = new EmailVerificationService(
      mock(EmailVerificationRepository.class),
      mock(MemberRepository.class),
      new BCryptPasswordEncoder(),
      mock(ObjectProvider.class),
      "",
      ""
    );
    ResponseStatusException error = assertThrows(
      ResponseStatusException.class,
      () ->
        service.sendLink(
          "person@example.com",
          "http://localhost/api/users/email/confirm"
        )
    );
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
  }
}
