package kr.or.sportmap.member.config;

import java.time.LocalDate;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 시작할 때 고정 관리자 계정을 준비한다. */
@Component
@ConditionalOnProperty(
  prefix = "app.admin",
  name = "seed-enabled",
  havingValue = "true"
)
public class LocalAdminSeed implements ApplicationRunner {

  private final MemberRepository members;
  private final PasswordEncoder passwords;
  private final String adminPassword;
  private final boolean requireStrongPassword;

  public LocalAdminSeed(
    MemberRepository members,
    PasswordEncoder passwords,
    @Value("${app.admin.initial-password}") String adminPassword,
    @Value(
      "${app.admin.require-strong-password:false}"
    ) boolean requireStrongPassword
  ) {
    this.members = members;
    this.passwords = passwords;
    this.adminPassword = adminPassword;
    this.requireStrongPassword = requireStrongPassword;
  }

  /** 계정이 없으면 생성하고, 기존 로컬 admin 계정의 역할과 비밀번호를 맞춘다. */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (requireStrongPassword && !isStrong(adminPassword)) {
      throw new IllegalStateException(
        "Submission admin password must be at least 12 characters and include upper/lowercase letters, a number, and a special character"
      );
    }
    Member admin = members
      .findByUsername("admin")
      .orElseGet(() ->
        new Member(
          "관리자",
          "admin",
          passwords.encode(adminPassword),
          LocalDate.of(1990, 1, 1),
          "admin@sportmap.local",
          "01000000000",
          Member.Gender.OTHER
        )
      );
    admin.role = Member.Role.ADMIN;
    if (!passwords.matches(adminPassword, admin.passwordHash)) {
      admin.passwordHash = passwords.encode(adminPassword);
    }
    members.save(admin);
  }

  /** 제출 환경에서 공개되거나 단순한 관리자 비밀번호 사용을 차단한다. */
  private boolean isStrong(String value) {
    return (
      value != null &&
      value.length() >= 12 &&
      value.matches(".*[A-Z].*") &&
      value.matches(".*[a-z].*") &&
      value.matches(".*[0-9].*") &&
      value.matches(".*[^A-Za-z0-9].*") &&
      !"admin1234!".equals(value)
    );
  }
}
