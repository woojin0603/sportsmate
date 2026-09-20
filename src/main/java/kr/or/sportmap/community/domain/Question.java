package kr.or.sportmap.community.domain;

import jakarta.persistence.*;
import java.time.Instant;
import kr.or.sportmap.member.domain.Member;

/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
@Entity
@Table(name = "questions")
public class Question {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  public Member author;

  @Column(nullable = false, length = 200)
  public String title;

  @Column(nullable = false, length = 5000)
  public String content;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();

  protected Question() {}

  public Long getId() {
    return id;
  }

  public Question(Member author, String title, String content) {
    this.author = author;
    this.title = title;
    this.content = content;
  }
}
