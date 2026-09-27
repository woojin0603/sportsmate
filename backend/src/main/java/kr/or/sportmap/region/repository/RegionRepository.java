package kr.or.sportmap.region.repository;

import java.util.List;
import java.util.Optional;
import kr.or.sportmap.region.domain.Region;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface RegionRepository extends JpaRepository<Region, Long> {
  Optional<Region> findByCode(String code);

  @Override
  @EntityGraph(attributePaths = "parent")
  List<Region> findAll();
}
