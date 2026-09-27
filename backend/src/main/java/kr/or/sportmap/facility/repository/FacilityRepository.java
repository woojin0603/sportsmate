package kr.or.sportmap.facility.repository;

import java.util.Optional;
import kr.or.sportmap.facility.domain.Facility;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface FacilityRepository
  extends JpaRepository<Facility, Long>, FacilitySearchRepository
{
  @Override
  @EntityGraph(attributePaths = "region")
  Optional<Facility> findById(Long id);
}
