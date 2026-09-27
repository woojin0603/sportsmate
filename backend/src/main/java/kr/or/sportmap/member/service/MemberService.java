package kr.or.sportmap.member.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.repository.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 도메인 규칙과 외부 연동 작업을 처리한다. */
@Service
public class MemberService {

  private final MemberRepository repository;
  private final PasswordEncoder passwords;
  private final JwtEncoder jwtEncoder;
  private final EmailVerificationService emailVerifications;
  private final PhoneVerificationService phoneVerifications;
  private final JdbcTemplate jdbc;
  private final ConcurrentHashMap<String, LoginAttempt> loginAttempts =
    new ConcurrentHashMap<>();

  public MemberService(
    MemberRepository repository,
    PasswordEncoder passwords,
    JwtEncoder jwtEncoder,
    EmailVerificationService emailVerifications,
    PhoneVerificationService phoneVerifications,
    JdbcTemplate jdbc
  ) {
    this.repository = repository;
    this.passwords = passwords;
    this.jwtEncoder = jwtEncoder;
    this.emailVerifications = emailVerifications;
    this.phoneVerifications = phoneVerifications;
    this.jdbc = jdbc;
  }

  @Transactional
  public Member signup(
    String fullName,
    String username,
    String password,
    LocalDate birthDate,
    String email,
    String emailVerificationToken,
    String phoneNumber,
    String phoneRequestToken,
    String phoneVerificationToken,
    Member.Gender gender
  ) {
    String normalizedUsername = username.trim();
    if (
      "admin".equalsIgnoreCase(normalizedUsername)
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "예약된 아이디입니다"
    );
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    String normalizedPhone = PhoneVerificationService.normalize(phoneNumber);
    if (
      normalizedPhone.length() < 10 || normalizedPhone.length() > 11
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "전화번호는 숫자 10~11자리여야 합니다"
    );
    if (
      password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다"
    );
    if (
      repository.existsByUsername(normalizedUsername)
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "이미 사용 중인 아이디입니다"
    );
    if (
      repository.existsByEmail(normalizedEmail)
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "이미 사용 중인 이메일입니다"
    );
    phoneVerifications.consume(
      normalizedPhone,
      phoneRequestToken,
      phoneVerificationToken
    );
    emailVerifications.consume(normalizedEmail, emailVerificationToken);
    try {
      return repository.saveAndFlush(
        new Member(
          fullName.trim(),
          normalizedUsername,
          passwords.encode(password),
          birthDate,
          normalizedEmail,
          normalizedPhone,
          gender
        )
      );
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "아이디 또는 이메일이 이미 사용 중입니다"
      );
    }
  }

  @Transactional(readOnly = true)
  public Member login(String username, String password) {
    String key = username.trim().toLowerCase(Locale.ROOT);
    Instant now = Instant.now();
    loginAttempts
      .entrySet()
      .removeIf(entry -> entry.getValue().blockedUntil().isBefore(now));
    LoginAttempt attempt = loginAttempts.get(key);
    if (
      attempt != null &&
      attempt.failures() >= 5 &&
      attempt.blockedUntil().isAfter(now)
    ) {
      throw new ResponseStatusException(
        HttpStatus.TOO_MANY_REQUESTS,
        "로그인 시도가 너무 많습니다. 10분 후 다시 시도해 주세요"
      );
    }
    Member member = repository.findByUsername(username.trim()).orElse(null);
    if (member == null || !passwords.matches(password, member.passwordHash)) {
      int failures = attempt == null ? 1 : attempt.failures() + 1;
      loginAttempts.put(
        key,
        new LoginAttempt(failures, now.plus(10, ChronoUnit.MINUTES))
      );
      throw new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "아이디 또는 비밀번호가 올바르지 않습니다"
      );
    }
    loginAttempts.remove(key);
    return member;
  }

  @Transactional(readOnly = true)
  public Member findAuthenticated(String subject) {
    try {
      return repository
        .findById(Long.valueOf(subject))
        .orElseThrow(() ->
          new ResponseStatusException(HttpStatus.UNAUTHORIZED)
        );
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
  }

  /** 현재 비밀번호를 확인한 뒤 새 비밀번호를 BCrypt로 저장한다. */
  @Transactional
  public void changePassword(
    String subject,
    String currentPassword,
    String newPassword
  ) {
    Member member = findAuthenticated(subject);
    if (!passwords.matches(currentPassword, member.passwordHash)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "현재 비밀번호가 올바르지 않습니다"
      );
    }
    if (
      newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "새 비밀번호는 UTF-8 기준 72바이트 이하여야 합니다"
      );
    }
    member.passwordHash = passwords.encode(newPassword);
  }

  /** 회원과 직접 연결된 활동 데이터를 함께 삭제한다. */
  @Transactional
  public void withdraw(String subject, String password) {
    Member member = findAuthenticated(subject);
    if (member.getRole() == Member.Role.ADMIN) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "관리자 계정은 탈퇴할 수 없습니다"
      );
    }
    if (!passwords.matches(password, member.passwordHash)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "비밀번호가 올바르지 않습니다"
      );
    }
    Long id = member.id;
    jdbc.update(
      "update comments set parent_id = null where parent_id in (select id from comments where author_id = ?)",
      id
    );
    jdbc.update(
      "delete from comments where author_id = ? or question_id in (select id from questions where author_id = ?)",
      id,
      id
    );
    jdbc.update("delete from questions where author_id = ?", id);
    jdbc.update("delete from reservations where member_id = ?", id);
    jdbc.update("delete from facility_reviews where member_id = ?", id);
    jdbc.update("delete from fitness_assessments where member_id = ?", id);
    jdbc.update("delete from member_abilities where member_id = ?", id);
    repository.delete(member);
  }

  public String issueToken(Member member) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
      .issuer("sportmap")
      .subject(member.id.toString())
      .claim("roles", java.util.List.of(member.getRole().name()))
      .issuedAt(now)
      .expiresAt(now.plus(15, ChronoUnit.MINUTES))
      .build();
    return jwtEncoder
      .encode(
        JwtEncoderParameters.from(
          JwsHeader.with(MacAlgorithm.HS256).build(),
          claims
        )
      )
      .getTokenValue();
  }

  private record LoginAttempt(int failures, Instant blockedUntil) {}
}
