package kr.or.sportmap.community.domain;

import jakarta.persistence.*;
import java.time.Instant;
import kr.or.sportmap.member.domain.Member;

/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
@Entity
@Table(name = "comments")
public class Comment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  public Question question;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  public Member author;

  @ManyToOne(fetch = FetchType.LAZY)
  public Comment parent;

  @Column(nullable = false, length = 3000)
  public String content;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();

  protected Comment() {}

  public Long getId() {
    return id;
  }

  public Comment(
    Question question,
    Member author,
    Comment parent,
    String content
  ) {
    this.question = question;
    this.author = author;
    this.parent = parent;
    this.content = content;
  }
}
