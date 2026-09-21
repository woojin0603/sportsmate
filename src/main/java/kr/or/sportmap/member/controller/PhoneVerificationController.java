package kr.or.sportmap.member.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import kr.or.sportmap.member.service.PhoneVerificationService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/phone")
public class PhoneVerificationController {

  private final PhoneVerificationService service;

  public PhoneVerificationController(PhoneVerificationService service) {
    this.service = service;
  }

  @PostMapping("/send")
  public ResponseEntity<PhoneVerificationService.SendResult> send(
    @Valid @RequestBody SendRequest body,
    HttpServletRequest request
  ) {
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(service.send(body.phoneNumber(), request.getRemoteAddr()));
  }

  @PostMapping("/verify")
  public ResponseEntity<PhoneVerificationService.VerifyResult> verify(
    @Valid @RequestBody VerifyRequest body
  ) {
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .body(
        service.verify(body.phoneNumber(), body.requestToken(), body.code())
      );
  }

  public record SendRequest(@NotBlank @Size(max = 15) String phoneNumber) {}

  public record VerifyRequest(
    @NotBlank @Size(max = 15) String phoneNumber,
    @NotBlank @Size(max = 100) String requestToken,
    @NotBlank @Pattern(regexp = "[0-9]{6}") String code
  ) {}
}
