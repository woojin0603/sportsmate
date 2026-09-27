package kr.or.sportmap.facility.repository;

import kr.or.sportmap.facility.domain.Facility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface FacilitySearchRepository {
  Page<Facility> search(
    String keyword,
    String regionCode,
    Boolean publicOnly,
    Pageable pageable
  );
}
