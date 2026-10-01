package kr.or.sportmap.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.server.ResponseStatusException;

class MemberServiceSecurityTest {

  private MemberRepository members;
  private JdbcTemplate jdbc;
  private MemberService service;
  private Member member;

  @BeforeEach
  void setUp() {
    members = mock(MemberRepository.class);
    jdbc = mock(JdbcTemplate.class);
    var encoder = new BCryptPasswordEncoder();
    member = new Member(
      "테스트 회원",
      "user1234",
      encoder.encode("Correct123!"),
      LocalDate.of(1995, 1, 1),
      "user@example.com",
      "01012345678",
      Member.Gender.OTHER
    );
    member.id = 7L;
    service = new MemberService(
      members,
      encoder,
      mock(JwtEncoder.class),
      mock(EmailVerificationService.class),
      jdbc
    );
  }

  /** 연속 로그인 실패가 허용 횟수를 넘으면 일정 시간 요청을 제한한다. */
  @Test
  void blocksRepeatedLoginFailures() {
    when(members.findByUsername("user1234")).thenReturn(Optional.of(member));
    for (int i = 0; i < 5; i++) {
      ResponseStatusException failure = assertThrows(
        ResponseStatusException.class,
        () -> service.login("user1234", "wrong-password")
      );
      assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
      assertEquals(
        "아이디 또는 비밀번호가 일치하지 않습니다.",
        failure.getReason()
      );
    }
    ResponseStatusException blocked = assertThrows(
      ResponseStatusException.class,
      () -> service.login("user1234", "Correct123!")
    );
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());
  }

  /** 탈퇴 계정 복구도 로그인과 같은 반복 실패 제한을 적용한다. */
  @Test
  void blocksRepeatedRestoreFailures() {
    when(members.findByUsername("user1234")).thenReturn(Optional.of(member));
    for (int i = 0; i < 5; i++) {
      ResponseStatusException failure = assertThrows(
        ResponseStatusException.class,
        () -> service.restore("user1234", "wrong-password")
      );
      assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
    }
    ResponseStatusException blocked = assertThrows(
      ResponseStatusException.class,
      () -> service.restore("user1234", "Correct123!")
    );
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());
  }

  /** 회원 탈퇴는 즉시 삭제하지 않고 7일 복구 유예 상태로 전환한다. */
  @Test
  void defersDeletionAndAllowsRestoration() {
    when(members.findById(7L)).thenReturn(Optional.of(member));
    when(members.findByUsername("user1234")).thenReturn(Optional.of(member));
    service.withdraw("7", "Correct123!");
    assertNotNull(member.deletionRequestedAt);
    service.restore("user1234", "Correct123!");
    assertNull(member.deletionRequestedAt);
  }
}
