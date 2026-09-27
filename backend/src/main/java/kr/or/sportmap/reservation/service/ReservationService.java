package kr.or.sportmap.reservation.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import kr.or.sportmap.member.service.MemberService;
import kr.or.sportmap.program.repository.ProgramRepository;
import kr.or.sportmap.reservation.domain.Reservation;
import kr.or.sportmap.reservation.repository.ReservationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 도메인 규칙과 외부 연동 작업을 처리한다. */
@Service
public class ReservationService {

  private final ReservationRepository reservations;
  private final ProgramRepository programs;
  private final MemberService members;

  public ReservationService(
    ReservationRepository reservations,
    ProgramRepository programs,
    MemberService members
  ) {
    this.reservations = reservations;
    this.programs = programs;
    this.members = members;
  }

  @Transactional
  public Reservation create(
    String memberSubject,
    Long programId,
    Instant startsAt,
    Instant endsAt
  ) {
    var program = programs
      .findForReservation(programId)
      .orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "프로그램 없음")
      );
    if (program.sourceKey != null) {
      if (startsAt != null || endsAt != null) throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "공공 프로그램 신청은 programId만 보내세요"
      );
      if (
        program.endsOn == null ||
        program.endsOn.isBefore(LocalDate.now(ZoneId.of("Asia/Seoul")))
      ) throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "종료된 프로그램입니다"
      );
      var member = members.findAuthenticated(memberSubject);
      if (
        reservations.existsByMemberIdAndProgramIdAndStatusNot(
          member.getId(),
          programId,
          Reservation.Status.CANCELLED
        )
      ) throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "이미 신청한 프로그램입니다"
      );
      Instant periodStart = program.beginsOn
        .atStartOfDay(ZoneId.of("Asia/Seoul"))
        .toInstant();
      Instant periodEnd = program.endsOn
        .plusDays(1)
        .atStartOfDay(ZoneId.of("Asia/Seoul"))
        .toInstant();
      return reservations.save(
        new Reservation(
          member,
          program,
          periodStart,
          periodEnd,
          Reservation.Status.REQUESTED
        )
      );
    }
    if (
      startsAt == null ||
      endsAt == null ||
      !startsAt.isAfter(Instant.now()) ||
      !endsAt.isAfter(startsAt)
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "예약 시간을 확인하세요"
    );
    if (
      !Boolean.TRUE.equals(program.bookingSupported) ||
      program.capacity == null ||
      program.capacity < 1
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "이 프로그램은 현재 예약할 수 없습니다"
    );
    if (
      reservations.countOverlapping(
        programId,
        startsAt,
        endsAt,
        Reservation.Status.CONFIRMED
      ) >= program.capacity
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "정원이 마감되었습니다"
    );
    return reservations.save(
      new Reservation(
        members.findAuthenticated(memberSubject),
        program,
        startsAt,
        endsAt
      )
    );
  }

  @Transactional(readOnly = true)
  public Page<Reservation> mine(String memberSubject, Pageable pageable) {
    return reservations.findByMemberId(
      members.findAuthenticated(memberSubject).getId(),
      pageable
    );
  }

  @Transactional(readOnly = true)
  public Reservation one(String memberSubject, Long id) {
    Reservation r = reservations
      .findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (
      !r.member.getId().equals(members.findAuthenticated(memberSubject).getId())
    ) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return r;
  }

  @Transactional
  public Reservation cancel(String memberSubject, Long id) {
    Reservation r = one(memberSubject, id);
    if (r.status == Reservation.Status.CANCELLED) return r;
    if (
      r.status == Reservation.Status.CONFIRMED &&
      !r.startsAt.isAfter(Instant.now())
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "시작된 예약은 취소할 수 없습니다"
    );
    r.cancel();
    return r;
  }
}
