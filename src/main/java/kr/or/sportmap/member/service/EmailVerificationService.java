package kr.or.sportmap.member.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import kr.or.sportmap.member.domain.EmailVerification;
import kr.or.sportmap.member.repository.EmailVerificationRepository;
import kr.or.sportmap.member.repository.MemberRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 메일 인증 링크 발송, 링크 확인, 회원가입용 일회용 토큰 발급을 담당한다. */
@Service
public class EmailVerificationService {

  private static final Duration LINK_LIFETIME = Duration.ofMinutes(10);
  private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
  private final EmailVerificationRepository verifications;
  private final MemberRepository members;
  private final PasswordEncoder passwords;
  private final ObjectProvider<JavaMailSender> mailSenders;
  private final String mailHost;
  private final String from;

  public EmailVerificationService(
    EmailVerificationRepository verifications,
    MemberRepository members,
    PasswordEncoder passwords,
    ObjectProvider<JavaMailSender> mailSenders,
    @Value("${spring.mail.host:}") String mailHost,
    @Value("${app.mail.from:}") String from
  ) {
    this.verifications = verifications;
    this.members = members;
    this.passwords = passwords;
    this.mailSenders = mailSenders;
    this.mailHost = mailHost;
    this.from = from;
  }

  /** 인증 버튼이 포함된 HTML 메일을 발송하고 화면 확인용 요청 키를 반환한다. */
  @Transactional
  public String sendLink(String address, String confirmationBaseUrl) {
    String email = normalize(address);
    if (members.existsByEmail(email)) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "이미 가입된 이메일입니다"
    );
    JavaMailSender sender = mailSenders.getIfAvailable();
    if (
      mailHost.isBlank() || from.isBlank() || sender == null
    ) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "이메일 발송 설정이 필요합니다. 관리자에게 문의해 주세요"
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
    String confirmationUrl =
      confirmationBaseUrl + "?token=" + confirmationToken;
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(
        message,
        false,
        StandardCharsets.UTF_8.name()
      );
      helper.setFrom(from);
      helper.setTo(email);
      helper.setSubject("[SportMap] 회원가입 이메일 인증");
      helper.setText(
        "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;padding:32px;color:#173e32\"><h2>SportMap 이메일 인증</h2><p>아래 버튼을 눌러 이메일 인증을 완료해 주세요.</p><a href=\"" +
          confirmationUrl +
          "\" style=\"display:inline-block;margin:20px 0;padding:14px 24px;background:#173e32;color:white;text-decoration:none;border-radius:10px;font-weight:bold\">이메일 인증 완료</a><p style=\"color:#66766d;font-size:14px\">이 링크는 10분 동안 유효합니다. 요청하지 않았다면 이 메일을 무시하세요.</p></div>",
        true
      );
      sender.send(message);
    } catch (MailAuthenticationException error) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Gmail 앱 비밀번호가 올바르지 않습니다. SMTP 설정을 다시 확인해 주세요",
        error
      );
    } catch (MailException | MessagingException error) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요",
        error
      );
    }
    verification.codeHash = null;
    verification.tokenHash = null;
    verification.requestHash = passwords.encode(requestToken);
    verification.confirmationHash = sha256(confirmationToken);
    verification.sentAt = now;
    verification.expiresAt = now.plus(LINK_LIFETIME);
    verification.verifiedAt = null;
    verification.failedAttempts = 0;
    verifications.save(verification);
    return requestToken;
  }

  /** 메일의 인증 버튼 토큰을 확인해 이메일을 인증 완료 상태로 바꾼다. */
  @Transactional
  public void confirm(String confirmationToken) {
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
      !passwords.matches(requestToken, verification.requestHash)
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
    if (verification.verifiedAt == null) return new VerificationStatus(
      false,
      null
    );
    String signupToken = UUID.randomUUID().toString();
    verification.tokenHash = passwords.encode(signupToken);
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
      !passwords.matches(token, verification.tokenHash)
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "이메일 인증을 다시 진행해 주세요"
    );
    verifications.delete(verification);
  }

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

  public record VerificationStatus(
    boolean verified,
    String verificationToken
  ) {}
}
