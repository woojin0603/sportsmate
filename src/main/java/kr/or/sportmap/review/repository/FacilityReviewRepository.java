package kr.or.sportmap.review.repository;

import kr.or.sportmap.review.domain.FacilityReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface FacilityReviewRepository
  extends JpaRepository<FacilityReview, Long>
{
  Page<FacilityReview> findByFacilityId(Long facilityId, Pageable pageable);
}
