package kr.or.sportmap.member.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import kr.or.sportmap.member.domain.PhoneVerification;
import kr.or.sportmap.member.domain.SmsBudget;
import kr.or.sportmap.member.repository.PhoneVerificationRepository;
import kr.or.sportmap.member.repository.SmsBudgetRepository;
import kr.or.sportmap.member.sms.SmsGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhoneVerificationService {

  private final PhoneVerificationRepository repository;
  private final SmsBudgetRepository budgets;
  private final PasswordEncoder passwords;
  private final SmsGateway gateway;
  private final int dailyLimit;
  private final SecureRandom random = new SecureRandom();
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public PhoneVerificationService(
    PhoneVerificationRepository repository,
    SmsBudgetRepository budgets,
    PasswordEncoder passwords,
    SmsGateway gateway,
    @Value("${app.sms.daily-limit:100}") int dailyLimit
  ) {
    this(
      repository,
      budgets,
      passwords,
      gateway,
      dailyLimit,
      Clock.systemUTC()
    );
  }

  PhoneVerificationService(
    PhoneVerificationRepository repository,
    SmsBudgetRepository budgets,
    PasswordEncoder passwords,
    SmsGateway gateway,
    int dailyLimit,
    Clock clock
  ) {
    this.repository = repository;
    this.budgets = budgets;
    this.passwords = passwords;
    this.gateway = gateway;
    this.dailyLimit = dailyLimit;
    this.clock = clock;
    if (dailyLimit < 1) throw new IllegalArgumentException(
      "SMS daily limit must be positive"
    );
  }

  @PostConstruct
  void initializeBudget() {
    if (!budgets.existsById(1L)) {
      try {
        budgets.saveAndFlush(new SmsBudget());
      } catch (DataIntegrityViolationException concurrentInitialization) {
        if (!budgets.existsById(1L)) throw concurrentInitialization;
      }
    }
  }

  // Persist quotas even when a provider rejects a request. Never retry delivery automatically.
  @Transactional(noRollbackFor = ResponseStatusException.class)
  public SendResult send(String rawPhone, String clientAddress) {
    String phone = normalize(rawPhone);
    if (!gateway.isEnabled()) throw error(
      HttpStatus.SERVICE_UNAVAILABLE,
      "문자 인증 서비스가 설정되지 않았습니다"
    );
    Instant now = clock.instant();
    SmsBudget budget = budgets.lockBudget().orElseThrow();
    repository.deleteBySentAtBefore(now.minus(Duration.ofDays(1)));
    var previous = repository.findPhoneLocked(phone);
    if (
      previous.stream().anyMatch(v -> now.isBefore(v.sentAt.plusSeconds(60)))
    ) {
      throw error(
        HttpStatus.TOO_MANY_REQUESTS,
        "1분 후 다시 발송할 수 있습니다"
      );
    }
    String clientKey = hash(clientAddress);
    if (
      previous.size() >= 5 ||
      repository.countByClientKeyAndSentAtAfter(
        clientKey,
        now.minusSeconds(3600)
      ) >= 10
    ) {
      throw error(
        HttpStatus.TOO_MANY_REQUESTS,
        "인증 요청 횟수를 초과했습니다. 나중에 다시 시도해 주세요"
      );
    }
    LocalDate day = LocalDate.ofInstant(now, ZoneOffset.UTC);
    if (!day.equals(budget.day)) {
      budget.day = day;
      budget.sentCount = 0;
    }
    if (budget.sentCount >= dailyLimit) throw error(
      HttpStatus.TOO_MANY_REQUESTS,
      "오늘의 문자 인증 요청 한도에 도달했습니다"
    );
    budget.sentCount++;
    for (var old : previous) {
      old.consumed = true;
      old.codeHash = null;
      old.tokenHash = null;
    }
    String requestToken = UUID.randomUUID().toString();
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    PhoneVerification verification = new PhoneVerification();
    verification.id = hash(requestToken);
    verification.phoneNumber = phone;
    verification.clientKey = clientKey;
    verification.sentAt = now;
    verification.expiresAt = now.plusSeconds(180);
    verification.codeHash = passwords.encode(code);
    verification.mockDelivery = gateway.isMock();
    verification = repository.saveAndFlush(verification);
    try {
      gateway.send(phone, code);
    } catch (RuntimeException deliveryError) {
      verification.consumed = true;
      verification.codeHash = null;
      throw error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "문자 발송을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요"
      );
    }
    return new SendResult(
      requestToken,
      verification.expiresAt,
      60,
      gateway.isMock(),
      gateway.isMock() ? code : null
    );
  }

  // Wrong attempts must commit; a normal rollback would allow unlimited guesses.
  @Transactional(noRollbackFor = ResponseStatusException.class)
  public VerifyResult verify(
    String rawPhone,
    String requestToken,
    String code
  ) {
    String phone = normalize(rawPhone);
    PhoneVerification verification = load(phone, requestToken);
    if (
      verification.verifiedAt != null ||
      verification.failedAttempts >= 5 ||
      verification.codeHash == null
    ) {
      throw invalid();
    }
    if (
      code == null ||
      !code.matches("[0-9]{6}") ||
      !passwords.matches(code, verification.codeHash)
    ) {
      verification.failedAttempts++;
      throw error(
        HttpStatus.BAD_REQUEST,
        "인증번호가 일치하지 않습니다. 5회 실패하면 다시 발송해야 합니다"
      );
    }
    String token = UUID.randomUUID().toString();
    verification.verifiedAt = clock.instant();
    verification.expiresAt = clock.instant().plusSeconds(600);
    verification.tokenHash = hash(token);
    verification.codeHash = null;
    return new VerifyResult(token, verification.expiresAt);
  }

  @Transactional
  public void consume(String phone, String requestToken, String token) {
    PhoneVerification verification = load(normalize(phone), requestToken);
    if (
      verification.verifiedAt == null ||
      token == null ||
      verification.tokenHash == null ||
      !MessageDigest.isEqual(
        hash(token).getBytes(StandardCharsets.UTF_8),
        verification.tokenHash.getBytes(StandardCharsets.UTF_8)
      )
    ) {
      throw invalid();
    }
    verification.consumed = true;
    verification.tokenHash = null;
  }

  private PhoneVerification load(String phone, String requestToken) {
    if (requestToken == null || requestToken.isBlank()) throw invalid();
    PhoneVerification verification = repository
      .findLocked(hash(requestToken))
      .orElseThrow(this::invalid);
    if (
      !phone.equals(verification.phoneNumber) ||
      verification.consumed ||
      !clock.instant().isBefore(verification.expiresAt) ||
      verification.mockDelivery != gateway.isMock()
    ) throw invalid();
    return verification;
  }

  public static String normalize(String raw) {
    if (raw == null || !raw.matches("[0-9 -]{11,15}")) throw error(
      HttpStatus.BAD_REQUEST,
      "010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요"
    );
    String phone = raw.replaceAll("[ -]", "");
    if (!phone.matches("010[0-9]{8}")) throw error(
      HttpStatus.BAD_REQUEST,
      "010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요"
    );
    return phone;
  }

  private static String hash(String value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          value.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  private ResponseStatusException invalid() {
    return error(
      HttpStatus.BAD_REQUEST,
      "휴대폰 인증이 유효하지 않거나 만료됐습니다. 다시 인증해 주세요"
    );
  }

  private static ResponseStatusException error(
    HttpStatus status,
    String message
  ) {
    return new ResponseStatusException(status, message);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SendResult(
    String requestToken,
    Instant expiresAt,
    int retryAfterSeconds,
    boolean mock,
    String developmentCode
  ) {}

  public record VerifyResult(String verificationToken, Instant expiresAt) {}
}
