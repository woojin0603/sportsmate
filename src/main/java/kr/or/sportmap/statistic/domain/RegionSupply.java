package kr.or.sportmap.statistic.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import kr.or.sportmap.region.domain.Region;

@Entity
@Table(
  name = "region_supplies",
  uniqueConstraints = @UniqueConstraint(
    columnNames = { "region_id", "base_year", "facility_type" }
  )
)
/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
public class RegionSupply {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "region_id")
  public Region region;

  @Column(name = "base_year", nullable = false)
  public Integer baseYear;

  @Column(name = "facility_type", nullable = false, length = 100)
  public String facilityType;

  public Integer facilityCount, population;

  @Column(precision = 14, scale = 4)
  public BigDecimal facilitiesPerTenThousand;

  protected RegionSupply() {}
}
