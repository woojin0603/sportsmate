package kr.or.sportmap.fitness.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import kr.or.sportmap.member.domain.Member;

@Entity
@Table(
  name = "fitness_assessments",
  indexes = @Index(columnList = "member_id,measured_on")
)
/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
public class FitnessAssessment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  public Member member;

  @Column(nullable = false)
  public LocalDate measuredOn;

  @Column(nullable = false)
  public int ageAtTest;

  @Column(nullable = false, length = 12)
  public String stage;

  @Column(nullable = false, length = 1)
  public String grade;

  @Column(nullable = false)
  public int score;

  public Integer equivalentOfficialGrade;

  @Column(nullable = false, length = 32)
  public String source;

  @Lob
  @Column(nullable = false)
  public String metricsJson;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();

  protected FitnessAssessment() {}

  public FitnessAssessment(
    Member member,
    LocalDate measuredOn,
    int ageAtTest,
    String stage,
    String grade,
    int score,
    Integer equivalentOfficialGrade,
    String source,
    String metricsJson
  ) {
    this.member = member;
    this.measuredOn = measuredOn;
    this.ageAtTest = ageAtTest;
    this.stage = stage;
    this.grade = grade;
    this.score = score;
    this.equivalentOfficialGrade = equivalentOfficialGrade;
    this.source = source;
    this.metricsJson = metricsJson;
  }
}
