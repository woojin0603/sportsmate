package kr.or.sportmap.reservation.repository;

import java.time.Instant;
import java.util.Optional;
import kr.or.sportmap.reservation.domain.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface ReservationRepository
  extends JpaRepository<Reservation, Long>
{
  boolean existsByMemberIdAndProgramIdAndStatusNot(
    Long memberId,
    Long programId,
    Reservation.Status status
  );

  @EntityGraph(attributePaths = { "member", "program" })
  Page<Reservation> findByMemberId(Long memberId, Pageable pageable);

  @Override
  @EntityGraph(attributePaths = { "member", "program" })
  Optional<Reservation> findById(Long id);

  @Query(
    "select count(r) from Reservation r where r.program.id = :programId and r.status = :status " +
      "and r.startsAt < :endsAt and r.endsAt > :startsAt"
  )
  long countOverlapping(
    @Param("programId") Long programId,
    @Param("startsAt") Instant startsAt,
    @Param("endsAt") Instant endsAt,
    @Param("status") Reservation.Status status
  );
}
