package kr.or.sportmap.review.domain;

import jakarta.persistence.*;
import java.time.Instant;
import kr.or.sportmap.facility.domain.Facility;
import kr.or.sportmap.member.domain.Member;

/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
@Entity
@Table(name = "facility_reviews")
public class FacilityReview {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  public Member member;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  public Facility facility;

  @Column(nullable = false)
  public Integer rating;

  @Column(nullable = false, length = 2000)
  public String content;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();

  protected FacilityReview() {}

  public FacilityReview(
    Member member,
    Facility facility,
    Integer rating,
    String content
  ) {
    this.member = member;
    this.facility = facility;
    this.rating = rating;
    this.content = content;
  }
}
