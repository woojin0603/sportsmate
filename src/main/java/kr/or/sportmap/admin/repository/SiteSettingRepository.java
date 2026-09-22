package kr.or.sportmap.admin.repository;

import kr.or.sportmap.admin.domain.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/** 저장된 사이트 설정을 조회한다. */
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {}
