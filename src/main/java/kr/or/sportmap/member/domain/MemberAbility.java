package kr.or.sportmap.member.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
  name = "member_abilities",
  uniqueConstraints = @UniqueConstraint(
    columnNames = { "member_id", "measured_on" }
  )
)
/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
public class MemberAbility {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id")
  public Member member;

  @Column(name = "measured_on", nullable = false)
  public LocalDate measuredOn;

  public Integer cardioScore, strengthScore, flexibilityScore, agilityScore, powerScore;

  @Column(length = 50)
  public String measurementSource;

  protected MemberAbility() {}
}
