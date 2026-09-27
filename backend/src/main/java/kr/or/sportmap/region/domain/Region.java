package kr.or.sportmap.region.domain;

import jakarta.persistence.*;

@Entity
@Table(
  name = "regions",
  uniqueConstraints = @UniqueConstraint(columnNames = "code")
)
/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
public class Region {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 20)
  public String code;

  @Column(nullable = false, length = 100)
  public String name;

  @ManyToOne(fetch = FetchType.LAZY)
  public Region parent;

  protected Region() {}

  public Long getId() {
    return id;
  }

  public Region(String code, String name, Region parent) {
    this.code = code;
    this.name = name;
    this.parent = parent;
  }
}
