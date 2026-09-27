package kr.or.sportmap.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 관리자 화면에서 수정하는 공개 사이트 설정이다. */
@Entity
@Table(name = "site_settings")
public class SiteSetting {

  @Id
  public Long id = 1L;

  @Column(nullable = false, length = 80)
  public String siteTitle = "SportMap";

  @Column(nullable = false, length = 500)
  public String announcement = "";

  @Column
  public Boolean popupEnabled = false;

  @Column(length = 120)
  public String popupTitle = "";

  @Column(length = 1000)
  public String popupContent = "";

  protected SiteSetting() {}

  public static SiteSetting defaults() {
    return new SiteSetting();
  }
}
