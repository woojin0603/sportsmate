package kr.or.sportmap.facility.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import kr.or.sportmap.region.domain.Region;

@Entity
@Table(
  name = "facilities",
  indexes = { @Index(columnList = "name"), @Index(columnList = "region_id") }
)
/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
public class Facility {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "region_id")
  public Region region;

  @Column(nullable = false, length = 200)
  public String name;

  @Column(length = 500)
  public String roadAddress;

  @Column(length = 100)
  public String type;

  @Column(precision = 10, scale = 7)
  public BigDecimal latitude;

  @Column(precision = 10, scale = 7)
  public BigDecimal longitude;

  @Column(length = 50)
  public String phone;

  @Column(length = 500)
  public String website;

  public Boolean publicFacility;
  public Boolean reservable;

  @Column(nullable = false)
  public Instant updatedAt = Instant.now();

  protected Facility() {}

  public Long getId() {
    return id;
  }

  public Facility(String name, Region region) {
    this.name = name;
    this.region = region;
  }
}
