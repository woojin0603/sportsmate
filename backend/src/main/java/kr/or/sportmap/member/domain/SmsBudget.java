package kr.or.sportmap.member.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/** Shared database lock and conservative daily cap, including failed sends. */
@Entity
@Table(name = "sms_budget")
public class SmsBudget {

  @Id
  public Long id = 1L;

  @Column(name = "budget_day")
  public LocalDate day;

  public int sentCount;
}
