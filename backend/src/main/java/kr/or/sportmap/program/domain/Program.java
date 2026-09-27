package kr.or.sportmap.program.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import kr.or.sportmap.facility.domain.Facility;

/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
@Entity
@Table(name = "programs")
public class Program {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  public Facility facility;

  @Column(nullable = false, length = 200)
  public String name;

  @Column(length = 100)
  public String sportType;

  @Column(length = 500)
  public String scheduleText;

  @Column(length = 500)
  public String eligibility;

  @Column(precision = 12, scale = 2)
  public BigDecimal fee;

  public Integer capacity;
  public Boolean bookingSupported;

  @Column(unique = true, length = 64)
  public String sourceKey;

  public LocalDate beginsOn;
  public LocalDate endsOn;

  @Column(length = 500)
  public String registrationUrl;

  protected Program() {}

  public static Program newImported() {
    return new Program();
  }

  public static Program newLocal() {
    return new Program();
  }

  public Long getId() {
    return id;
  }
}
