package kr.or.sportmap.program.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import kr.or.sportmap.program.domain.Program;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface ProgramRepository extends JpaRepository<Program, Long> {
  java.util.Optional<Program> findBySourceKey(String sourceKey);

  @EntityGraph(attributePaths = "facility")
  Page<Program> findByFacilityId(Long facilityId, Pageable pageable);

  @EntityGraph(attributePaths = "facility")
  Page<Program> findByNameContainingIgnoreCase(String name, Pageable pageable);

  /** 프로그램명과 시설 주소·지역명을 함께 적용해 프로그램을 검색한다. */
  @EntityGraph(attributePaths = { "facility", "facility.region" })
  @Query(
    """
    select p from Program p
    left join p.facility f
    left join f.region r
    where lower(p.name) like lower(concat('%', :keyword, '%'))
      and (
        :region = ''
        or lower(coalesce(f.roadAddress, '')) like lower(concat('%', :region, '%'))
        or lower(coalesce(r.name, '')) like lower(concat('%', :region, '%'))
      )
    """
  )
  Page<Program> search(
    @Param("keyword") String keyword,
    @Param("region") String region,
    Pageable pageable
  );

  /** 프로그램이 연결된 시설과 지역을 지역 필터 목록 생성용으로 조회한다. */
  @EntityGraph(attributePaths = { "facility", "facility.region" })
  @Query("select distinct p from Program p join p.facility f")
  List<Program> findAllForRegionOptions();

  @Override
  @EntityGraph(attributePaths = "facility")
  java.util.Optional<Program> findById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Program p where p.id = :id")
  java.util.Optional<Program> findForReservation(@Param("id") Long id);
}
