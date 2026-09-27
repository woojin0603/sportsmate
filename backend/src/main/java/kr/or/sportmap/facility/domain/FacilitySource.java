package kr.or.sportmap.facility.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
  name = "facility_sources",
  uniqueConstraints = @UniqueConstraint(
    columnNames = { "dataset_code", "source_key" }
  )
)
/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
public class FacilitySource {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  public Facility facility;

  @Column(name = "dataset_code", nullable = false, length = 80)
  public String datasetCode;

  @Column(name = "source_key", nullable = false, length = 200)
  public String sourceKey;

  @Column(nullable = false)
  public Instant fetchedAt;

  @Column(columnDefinition = "text")
  public String rawJson;

  protected FacilitySource() {}

  public FacilitySource(
    Facility facility,
    String datasetCode,
    String sourceKey,
    String rawJson
  ) {
    this.facility = facility;
    this.datasetCode = datasetCode;
    this.sourceKey = sourceKey;
    this.rawJson = rawJson;
    this.fetchedAt = Instant.now();
  }
}
