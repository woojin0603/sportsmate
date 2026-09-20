package kr.or.sportmap.member.config;

import java.time.LocalDate;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 로컬 테스트 환경에만 지정한 관리자 계정을 준비한다. */
@Component
@Profile("local")
public class LocalAdminSeed implements ApplicationRunner {

  private final MemberRepository members;
  private final PasswordEncoder passwords;
  private final String adminPassword;

  public LocalAdminSeed(
    MemberRepository members,
    PasswordEncoder passwords,
    @Value("${app.admin.initial-password:qwer1234!}") String adminPassword
  ) {
    this.members = members;
    this.passwords = passwords;
    this.adminPassword = adminPassword;
  }

  /** 계정이 없으면 생성하고, 기존 로컬 admin 계정의 역할과 비밀번호를 맞춘다. */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
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
}
