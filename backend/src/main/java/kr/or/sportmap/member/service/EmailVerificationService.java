package kr.or.sportmap.member.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.or.sportmap.member.domain.EmailVerification;
import kr.or.sportmap.member.mail.EmailGateway;
import kr.or.sportmap.member.repository.EmailVerificationRepository;
import kr.or.sportmap.member.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 메일 인증 링크 발송, 링크 확인, 회원가입용 일회용 토큰 발급을 담당한다. */
@Service
public class EmailVerificationService {

  private static final Duration LINK_LIFETIME = Duration.ofMinutes(10);
  private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
  private static final Duration CLIENT_WINDOW = Duration.ofHours(1);
  private static final int CLIENT_SEND_LIMIT = 10;
  private final EmailVerificationRepository verifications;
  private final MemberRepository members;
  private final EmailGateway gateway;
  private final ConcurrentHashMap<String, ArrayDeque<Instant>> clientRequests =
    new ConcurrentHashMap<>();

  public EmailVerificationService(
    EmailVerificationRepository verifications,
    MemberRepository members,
    EmailGateway gateway
  ) {
    this.verifications = verifications;
    this.members = members;
    this.gateway = gateway;
  }

  /** 인증 버튼이 포함된 HTML 메일을 발송하고 화면 확인용 요청 키를 반환한다. */
  @Transactional
  public SendResult sendLink(String address, String clientAddress) {
    String email = normalize(address);
    checkClientLimit(clientAddress);
    if (members.existsByEmail(email)) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "이미 가입된 이메일입니다"
    );
    EmailVerification verification = verifications
      .findByEmailForUpdate(email)
      .orElseGet(() -> new EmailVerification(email));
    Instant now = Instant.now();
    if (
      verification.sentAt != null &&
      now.isBefore(verification.sentAt.plus(RESEND_INTERVAL))
    ) throw new ResponseStatusException(
      HttpStatus.TOO_MANY_REQUESTS,
      "1분 후 다시 발송할 수 있습니다"
    );
    String confirmationToken = UUID.randomUUID().toString();
    String requestToken = UUID.randomUUID().toString();
    String confirmationUrl = gateway.confirmationUrl(confirmationToken);
    gateway.send(email, confirmationUrl);
    verification.mockDelivery = gateway.isMock();
    verification.codeHash = null;
    verification.tokenHash = null;
    verification.requestHash = sha256(requestToken);
    verification.confirmationHash = sha256(confirmationToken);
    verification.sentAt = now;
    verification.expiresAt = now.plus(LINK_LIFETIME);
    verification.verifiedAt = null;
    verification.failedAttempts = 0;
    verifications.save(verification);
    return new SendResult(
      requestToken,
      verification.expiresAt,
      60,
      gateway.isMock(),
      gateway.isMock() ? confirmationUrl : null
    );
  }

  /** 메일의 인증 버튼 토큰을 확인해 이메일을 인증 완료 상태로 바꾼다. */
  @Transactional
  public void confirm(String confirmationToken) {
    if (confirmationToken == null || confirmationToken.isBlank()) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "유효하지 않은 인증 링크입니다"
      );
    }
    EmailVerification verification = verifications
      .findByConfirmationHash(sha256(confirmationToken))
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "유효하지 않은 인증 링크입니다"
        )
      );
    if (
      verification.expiresAt == null ||
      !Instant.now().isBefore(verification.expiresAt)
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "인증 링크가 만료됐습니다. 회원가입 화면에서 다시 발송해 주세요"
    );
    requireMatchingMode(verification);
    verification.verifiedAt = Instant.now();
    verification.confirmationHash = null;
  }

  /** 회원가입 화면이 인증 완료 여부를 확인하고 완료되면 가입용 토큰을 받는다. */
  @Transactional
  public VerificationStatus status(String address, String requestToken) {
    EmailVerification verification = verifications
      .findByEmailForUpdate(normalize(address))
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "인증 메일을 먼저 발송해 주세요"
        )
      );
    if (
      verification.requestHash == null ||
      requestToken == null ||
      !constantTimeEquals(sha256(requestToken), verification.requestHash)
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "인증 요청이 올바르지 않습니다"
    );
    if (
      verification.expiresAt == null ||
      !Instant.now().isBefore(verification.expiresAt)
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "인증 링크가 만료됐습니다. 다시 발송해 주세요"
    );
    requireMatchingMode(verification);
    if (verification.verifiedAt == null) return new VerificationStatus(
      false,
      null
    );
    // Stable across repeated status checks; concurrent polling cannot invalidate a returned proof.
    String signupToken = sha256("email-signup:" + requestToken);
    if (verification.tokenHash == null) verification.tokenHash = sha256(
      signupToken
    );
    return new VerificationStatus(true, signupToken);
  }

  /** 확인된 이메일과 일회용 토큰을 검사하고 가입 성공 시 인증 상태를 삭제한다. */
  @Transactional
  public void consume(String address, String token) {
    EmailVerification verification = verifications
      .findByEmailForUpdate(normalize(address))
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "이메일 인증을 완료해 주세요"
        )
      );
    if (
      token == null ||
      token.isBlank() ||
      verification.tokenHash == null ||
      verification.verifiedAt == null ||
      verification.expiresAt == null ||
      !Instant.now().isBefore(verification.expiresAt) ||
      !constantTimeEquals(sha256(token), verification.tokenHash)
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "이메일 인증을 다시 진행해 주세요"
    );
    requireMatchingMode(verification);
    verifications.delete(verification);
  }

  private void requireMatchingMode(EmailVerification verification) {
    if (
      verification.mockDelivery == null ||
      verification.mockDelivery != gateway.isMock()
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "발송 모드가 변경됐습니다. 이메일 인증을 다시 진행해 주세요"
      );
    }
  }

  /** 한 클라이언트가 대량의 인증 메일을 발송하지 못하도록 시간당 요청 수를 제한한다. */
  private void checkClientLimit(String clientAddress) {
    String key = sha256(
      clientAddress == null || clientAddress.isBlank()
        ? "unknown"
        : clientAddress
    );
    Instant now = Instant.now();
    ArrayDeque<Instant> requests = clientRequests.computeIfAbsent(
      key,
      ignored -> new ArrayDeque<>()
    );
    synchronized (requests) {
      Instant cutoff = now.minus(CLIENT_WINDOW);
      while (!requests.isEmpty() && requests.peekFirst().isBefore(cutoff)) {
        requests.removeFirst();
      }
      if (
        requests.size() >= CLIENT_SEND_LIMIT
      ) throw new ResponseStatusException(
        HttpStatus.TOO_MANY_REQUESTS,
        "이메일 인증 요청 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요"
      );
      requests.addLast(now);
    }
    if (clientRequests.size() > 10_000) {
      clientRequests.entrySet().removeIf(entry -> {
        synchronized (entry.getValue()) {
          return (
            entry.getValue().isEmpty() ||
            entry.getValue().peekLast().isBefore(now.minus(CLIENT_WINDOW))
          );
        }
      });
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SendResult(
    String requestToken,
    Instant expiresAt,
    int retryAfterSeconds,
    boolean mock,
    String developmentConfirmationUrl
  ) {}

  private String normalize(String address) {
    return address.trim().toLowerCase(Locale.ROOT);
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          value.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  /** 고정 시간 비교로 인증 토큰 해시의 일치 여부를 확인한다. */
  private boolean constantTimeEquals(String supplied, String stored) {
    if (stored == null) return false;
    return MessageDigest.isEqual(
      supplied.getBytes(StandardCharsets.US_ASCII),
      stored.getBytes(StandardCharsets.US_ASCII)
    );
  }

  public record VerificationStatus(
    boolean verified,
    String verificationToken
  ) {}
}
