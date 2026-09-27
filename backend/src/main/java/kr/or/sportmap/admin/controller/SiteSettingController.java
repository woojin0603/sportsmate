package kr.or.sportmap.admin.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.or.sportmap.admin.domain.SiteSetting;
import kr.or.sportmap.admin.repository.SiteSettingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사이트에 표시할 설정을 공개 조회하고 관리자가 수정한다. */
@RestController
@RequestMapping("/api")
public class SiteSettingController {

  private final SiteSettingRepository settings;

  public SiteSettingController(SiteSettingRepository settings) {
    this.settings = settings;
  }

  /** 저장된 설정이 없으면 기본 표시값을 돌려준다. */
  @GetMapping("/site-settings")
  public SettingResponse get() {
    return SettingResponse.of(
      settings.findById(1L).orElseGet(SiteSetting::defaults)
    );
  }

  /** 관리자가 사이트 제목과 공통 안내 문구를 저장한다. */
  @PutMapping("/admin/site-settings")
  public SettingResponse update(@Valid @RequestBody SettingRequest request) {
    SiteSetting setting = settings
      .findById(1L)
      .orElseGet(SiteSetting::defaults);
    setting.siteTitle = request.siteTitle().trim();
    setting.announcement = request.announcement().trim();
    setting.popupEnabled = request.popupEnabled();
    setting.popupTitle =
      request.popupTitle() == null ? "" : request.popupTitle().trim();
    setting.popupContent =
      request.popupContent() == null ? "" : request.popupContent().trim();
    return SettingResponse.of(settings.save(setting));
  }

  public record SettingRequest(
    @NotBlank @Size(max = 80) String siteTitle,
    @Size(max = 500) String announcement,
    boolean popupEnabled,
    @Size(max = 120) String popupTitle,
    @Size(max = 1000) String popupContent
  ) {}

  public record SettingResponse(
    String siteTitle,
    String announcement,
    boolean popupEnabled,
    String popupTitle,
    String popupContent
  ) {
    static SettingResponse of(SiteSetting setting) {
      return new SettingResponse(
        setting.siteTitle,
        setting.announcement,
        Boolean.TRUE.equals(setting.popupEnabled),
        setting.popupTitle == null ? "" : setting.popupTitle,
        setting.popupContent == null ? "" : setting.popupContent
      );
    }
  }
}
