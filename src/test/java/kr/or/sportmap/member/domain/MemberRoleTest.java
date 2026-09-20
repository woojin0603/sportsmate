package kr.or.sportmap.member.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MemberRoleTest {

  /** 새 회원과 기존 DB의 역할 누락 회원은 모두 일반 사용자로 취급한다. */
  @Test
  void defaultsToUser() {
    Member member = new Member(
      "테스트",
      "test_user",
      "hash",
      LocalDate.of(2000, 1, 1),
      "test@example.com",
      "01012345678",
      Member.Gender.OTHER
    );
    assertEquals(Member.Role.USER, member.getRole());
    member.role = null;
    assertEquals(Member.Role.USER, member.getRole());
  }
}
