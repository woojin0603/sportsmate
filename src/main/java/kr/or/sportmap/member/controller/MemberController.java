package kr.or.sportmap.member.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.service.EmailVerificationService;
import kr.or.sportmap.member.service.MemberService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/users")
public class MemberController {

  private final MemberService service;
  private final EmailVerificationService emailVerifications;
  private final boolean secureCookie;

  public MemberController(
    MemberService service,
    EmailVerificationService emailVerifications,
    @Value("${app.auth.secure-cookie:true}") boolean secureCookie
  ) {
    this.service = service;
    this.emailVerifications = emailVerifications;
    this.secureCookie = secureCookie;
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public MemberResponse signup(@Valid @RequestBody SignupRequest request) {
    return MemberResponse.of(
      service.signup(
        request.fullName(),
        request.username(),
        request.password(),
        request.birthDate(),
        request.email(),
        request.emailVerificationToken(),
        request.phoneNumber(),
        request.phoneRequestToken(),
        request.phoneVerificationToken(),
        request.gender()
      )
    );
  }

  /** 가입 이메일로 인증 버튼이 포함된 메일을 보낸다. */
  @PostMapping("/email/send")
  public ResponseEntity<EmailVerificationService.SendResult> sendEmailLink(
    @Valid @RequestBody EmailSendRequest request
  ) {
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(emailVerifications.sendLink(request.email()));
  }

  /** 메일의 인증 버튼을 누르면 인증을 완료하고 안내 화면을 보여준다. */
  @GetMapping(value = "/email/confirm", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> confirmEmail(@RequestParam String token) {
    emailVerifications.confirm(token);
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .header("Referrer-Policy", "no-referrer")
      .body(
        "<!doctype html><html lang=\"ko\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>SportMap 이메일 인증</title><body style=\"font-family:sans-serif;background:#f5f8f3;color:#173e32;display:grid;place-items:center;min-height:100vh;margin:0\"><main style=\"background:white;padding:40px;border-radius:18px;text-align:center;box-shadow:0 15px 50px #173e3222\"><h1>이메일 인증 완료</h1><p>SportMap 회원가입 화면으로 돌아가 회원가입을 완료해 주세요.</p></main></body></html>"
      );
  }

  /** 회원가입 화면에서 메일 인증 완료 여부를 확인한다. */
  @PostMapping("/email/status")
  public ResponseEntity<EmailStatusResponse> emailStatus(
    @Valid @RequestBody EmailStatusRequest request
  ) {
    var status = emailVerifications.status(
      request.email(),
      request.requestToken()
    );
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(
        new EmailStatusResponse(status.verified(), status.verificationToken())
      );
  }

  @PostMapping("/login")
  public MemberResponse login(
    @Valid @RequestBody LoginRequest request,
    HttpServletResponse response
  ) {
    Member member = service.login(request.username(), request.password());
    response.addHeader(
      HttpHeaders.SET_COOKIE,
      cookie(service.issueToken(member), Duration.ofMinutes(15))
    );
    return MemberResponse.of(member);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO));
  }

  @GetMapping("/mypage")
  public MemberResponse mypage(@AuthenticationPrincipal Jwt jwt) {
    return MemberResponse.of(service.findAuthenticated(jwt.getSubject()));
  }

  private String cookie(String value, Duration age) {
    return ResponseCookie.from("ACCESS_TOKEN", value)
      .httpOnly(true)
      .secure(secureCookie)
      .sameSite("Strict")
      .path("/api")
      .maxAge(age)
      .build()
      .toString();
  }

  public record SignupRequest(
    @NotBlank @Size(max = 50) String fullName,
    @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{3,29}") String username,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotNull @Past LocalDate birthDate,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 100) String emailVerificationToken,
    @NotBlank @Size(max = 15) String phoneNumber,
    @NotBlank @Size(max = 100) String phoneRequestToken,
    @NotBlank @Size(max = 100) String phoneVerificationToken,
    @NotNull Member.Gender gender
  ) {}

  public record EmailSendRequest(
    @NotBlank @Email @Size(max = 255) String email
  ) {}

  public record EmailStatusRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 100) String requestToken
  ) {}

  public record EmailStatusResponse(
    boolean verified,
    String verificationToken
  ) {}

  public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password
  ) {}

  public record MemberResponse(
    Long id,
    String fullName,
    String username,
    LocalDate birthDate,
    int age,
    String email,
    String phoneNumber,
    Member.Gender gender,
    Member.Role role
  ) {
    static MemberResponse of(Member m) {
      return new MemberResponse(
        m.id,
        m.fullName,
        m.username,
        m.birthDate,
        Period.between(
          m.birthDate,
          LocalDate.now(ZoneId.of("Asia/Seoul"))
        ).getYears(),
        m.email,
        m.phoneNumber,
        m.gender,
        m.getRole()
      );
    }
  }
}
