package kr.or.sportmap.community.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
@Entity
@Table(name = "notices")
public class Notice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 200)
  public String title;

  @Column(nullable = false, length = 10000)
  public String content;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();

  protected Notice() {}

  public Notice(String title, String content) {
    this.title = title;
    this.content = content;
  }
}
