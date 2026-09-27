package kr.or.sportmap.facility.repository;

import java.util.Optional;
import kr.or.sportmap.facility.domain.FacilitySource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface FacilitySourceRepository
  extends JpaRepository<FacilitySource, Long>
{
  @EntityGraph(attributePaths = "facility")
  Optional<FacilitySource> findByDatasetCodeAndSourceKey(
    String datasetCode,
    String sourceKey
  );
}
