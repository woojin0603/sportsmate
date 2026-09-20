package kr.or.sportmap.member.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 회원가입 전에 이메일 소유권을 확인하는 일회용 인증 상태다. */
@Entity
@Table(
  name = "email_verifications",
  uniqueConstraints = @UniqueConstraint(columnNames = "email")
)
public class EmailVerification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 255)
  public String email;

  @Column(length = 100)
  public String codeHash;

  @Column(length = 100)
  public String tokenHash;

  @Column(length = 100)
  public String requestHash;

  @Column(length = 64)
  public String confirmationHash;

  public Instant sentAt;
  public Instant expiresAt;
  public Instant verifiedAt;
  public int failedAttempts;

  protected EmailVerification() {}

  public EmailVerification(String email) {
    this.email = email;
  }
}
