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
import org.springframework.scheduling.annotation.Scheduled;
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
  private final JdbcTemplate jdbc;
  private final ConcurrentHashMap<String, LoginAttempt> loginAttempts =
    new ConcurrentHashMap<>();

  public MemberService(
    MemberRepository repository,
    PasswordEncoder passwords,
    JwtEncoder jwtEncoder,
    EmailVerificationService emailVerifications,
    JdbcTemplate jdbc
  ) {
    this.repository = repository;
    this.passwords = passwords;
    this.jwtEncoder = jwtEncoder;
    this.emailVerifications = emailVerifications;
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
    String normalizedPhone = normalizePhone(phoneNumber);
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
    if (member.deletionRequestedAt != null) {
      HttpStatus status = member.deletionRequestedAt.plus(7, ChronoUnit.DAYS).isAfter(now)
        ? HttpStatus.LOCKED
        : HttpStatus.GONE;
      throw new ResponseStatusException(
        status,
        status == HttpStatus.LOCKED
          ? "탈퇴 유예 중인 계정입니다. 계정 복구를 이용해 주세요"
          : "탈퇴 처리된 계정입니다"
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

  /** 탈퇴 요청을 기록하고 7일 동안 개인정보와 활동 데이터를 보존한다. */
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
    member.deletionRequestedAt = Instant.now();
  }

  /** 유예기간 안에 본인 인증 후 탈퇴 요청을 취소한다. */
  @Transactional
  public Member restore(String username, String password) {
    Member member = repository.findByUsername(username.trim()).orElseThrow(() ->
      new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다")
    );
    if (!passwords.matches(password, member.passwordHash)) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "아이디 또는 비밀번호가 올바르지 않습니다"
    );
    if (member.deletionRequestedAt == null) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "탈퇴 유예 중인 계정이 아닙니다"
    );
    if (!member.deletionRequestedAt.plus(7, ChronoUnit.DAYS).isAfter(Instant.now())) {
      hardDelete(member);
      throw new ResponseStatusException(HttpStatus.GONE, "복구 가능 기간이 지났습니다");
    }
    member.deletionRequestedAt = null;
    return member;
  }

  /** 관리자가 이메일 인증 없이 회원을 등록한다. */
  @Transactional
  public Member createByAdmin(String fullName, String username, String password, LocalDate birthDate, String email, String phoneNumber, Member.Gender gender, Member.Role role) {
    String normalizedUsername = username.trim();
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    if (repository.existsByUsername(normalizedUsername) || repository.existsByEmail(normalizedEmail)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "아이디 또는 이메일이 이미 사용 중입니다");
    }
    Member member = new Member(fullName.trim(), normalizedUsername, passwords.encode(password), birthDate, normalizedEmail, normalizePhone(phoneNumber), gender);
    member.role = role;
    return repository.saveAndFlush(member);
  }

  /** 관리자 삭제 및 유예기간 만료 시 회원 활동과 계정을 영구 삭제한다. */
  @Transactional
  public void hardDelete(Member member) {
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

  @Transactional
  public void hardDeleteById(Long id) {
    Member member = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if ("admin".equals(member.username)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기본 관리자 계정은 삭제할 수 없습니다");
    hardDelete(member);
  }

  /** 관리자가 탈퇴 유예 중인 회원을 즉시 활성 상태로 복구한다. */
  @Transactional
  public Member restoreByAdmin(Long id) {
    Member member = repository.findById(id).orElseThrow(() ->
      new ResponseStatusException(HttpStatus.NOT_FOUND)
    );
    if (member.deletionRequestedAt == null) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "탈퇴 상태인 회원이 아닙니다"
    );
    member.deletionRequestedAt = null;
    return member;
  }

  /** 매일 새벽 유예기간이 끝난 탈퇴 계정을 영구 삭제한다. */
  @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Seoul")
  @Transactional
  public void purgeExpiredWithdrawals() {
    repository.findByDeletionRequestedAtBefore(Instant.now().minus(7, ChronoUnit.DAYS)).forEach(this::hardDelete);
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

  /** 전화번호는 발송 인증 없이 국내 휴대전화 형식만 검증해 저장한다. */
  private String normalizePhone(String rawPhone) {
    if (rawPhone == null) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "010으로 시작하는 휴대폰 번호를 입력해 주세요"
    );
    String phone = rawPhone.replaceAll("[ -]", "");
    if (!phone.matches("010[0-9]{8}")) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요"
    );
    return phone;
  }

  private record LoginAttempt(int failures, Instant blockedUntil) {}
}
