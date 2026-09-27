package kr.or.sportmap.member.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import kr.or.sportmap.region.domain.Region;

@Entity
@Table(
  name = "members",
  uniqueConstraints = {
    @UniqueConstraint(columnNames = "email"),
    @UniqueConstraint(columnNames = "username"),
  }
)
/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
public class Member {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 30)
  public String username;

  @Column(nullable = false, length = 255)
  public String email;

  @Column(nullable = false)
  public String passwordHash;

  @Column(nullable = false, length = 50)
  public String fullName;

  @Column(nullable = false)
  public LocalDate birthDate;

  @Column(nullable = false, length = 20)
  public String phoneNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  public Gender gender;

  // 기존 회원 행의 null은 USER로 해석해 DB 갱신 시 로그인을 유지한다.
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  public Role role = Role.USER;

  @ManyToOne(fetch = FetchType.LAZY)
  public Region region;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();

  protected Member() {}

  public Long getId() {
    return id;
  }

  public Member(
    String fullName,
    String username,
    String passwordHash,
    LocalDate birthDate,
    String email,
    String phoneNumber,
    Gender gender
  ) {
    this.fullName = fullName;
    this.username = username;
    this.passwordHash = passwordHash;
    this.birthDate = birthDate;
    this.email = email;
    this.phoneNumber = phoneNumber;
    this.gender = gender;
    this.role = Role.USER;
  }

  /** 기존 회원 데이터를 포함해 유효한 계정 권한을 반환한다. */
  public Role getRole() {
    return role == null ? Role.USER : role;
  }

  public enum Gender {
    MALE,
    FEMALE,
    OTHER,
  }

  public enum Role {
    USER,
    ADMIN,
  }
}
