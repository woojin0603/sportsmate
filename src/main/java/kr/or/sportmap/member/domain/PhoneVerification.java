package kr.or.sportmap.member.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
  name = "phone_verifications",
  indexes = {
    @Index(
      name = "idx_phone_verification_phone",
      columnList = "phoneNumber,sentAt"
    ),
    @Index(
      name = "idx_phone_verification_client",
      columnList = "clientKey,sentAt"
    ),
  }
)
public class PhoneVerification {

  @Id
  @Column(length = 64)
  public String id;

  @Column(nullable = false, length = 11)
  public String phoneNumber;

  @Column(nullable = false, length = 64)
  public String clientKey;

  @Column(length = 100)
  public String codeHash;

  @Column(length = 64)
  public String tokenHash;

  @Column(nullable = false)
  public Instant sentAt;

  @Column(nullable = false)
  public Instant expiresAt;

  public Instant verifiedAt;
  public int failedAttempts;
  public boolean consumed;
  public boolean mockDelivery;
}
