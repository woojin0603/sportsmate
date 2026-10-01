package kr.or.sportmap.reservation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.program.domain.Program;

/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
@Entity
@Table(name = "reservations")
public class Reservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  public Member member;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  public Program program;

  // 운영 기간을 제공하지 않는 공공 프로그램은 관심 일정으로만 저장한다.
  public Instant startsAt;

  public Instant endsAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public Status status;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();

  /** 회원이 프로그램 시작일 알림을 확인한 시각이다. */
  public Instant startNotificationReadAt;

  public enum Status {
    REQUESTED,
    CONFIRMED,
    CANCELLED,
  }

  protected Reservation() {}

  public Reservation(
    Member member,
    Program program,
    Instant startsAt,
    Instant endsAt
  ) {
    this(member, program, startsAt, endsAt, Status.CONFIRMED);
  }

  public Reservation(
    Member member,
    Program program,
    Instant startsAt,
    Instant endsAt,
    Status status
  ) {
    this.member = member;
    this.program = program;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.status = status;
  }

  public void cancel() {
    this.status = Status.CANCELLED;
  }
}
