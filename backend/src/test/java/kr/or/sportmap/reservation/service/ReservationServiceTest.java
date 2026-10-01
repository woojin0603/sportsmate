package kr.or.sportmap.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.or.sportmap.member.domain.Member;
import kr.or.sportmap.member.service.MemberService;
import kr.or.sportmap.program.domain.Program;
import kr.or.sportmap.program.repository.ProgramRepository;
import kr.or.sportmap.reservation.domain.Reservation;
import kr.or.sportmap.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

  @Mock
  ReservationRepository reservations;

  @Mock
  ProgramRepository programs;

  @Mock
  MemberService members;

  /** 운영 기간이 없는 공공 프로그램도 관심 일정으로 저장할 수 있어야 한다. */
  @Test
  void createsRequestedReservationWithoutInventingSchedule() {
    Program program = Program.newImported();
    program.id = 7L;
    program.sourceKey = "public-program-7";
    Member member = new Member(
      "테스트",
      "tester",
      "hash",
      java.time.LocalDate.of(1990, 1, 1),
      "tester@example.com",
      "01012345678",
      Member.Gender.OTHER
    );
    member.id = 3L;

    when(programs.findForReservation(7L)).thenReturn(Optional.of(program));
    when(members.findAuthenticated("tester")).thenReturn(member);
    when(
      reservations.existsByMemberIdAndProgramIdAndStatusNot(
        3L,
        7L,
        Reservation.Status.CANCELLED
      )
    ).thenReturn(false);
    when(reservations.save(any(Reservation.class))).thenAnswer(invocation ->
      invocation.getArgument(0)
    );

    ReservationService service = new ReservationService(
      reservations,
      programs,
      members
    );
    Reservation result = service.create("tester", 7L, null, null);

    assertThat(result.status).isEqualTo(Reservation.Status.REQUESTED);
    assertThat(result.startsAt).isEqualTo(
      java.time.Instant.parse("2000-01-01T00:00:00Z")
    );
    assertThat(result.endsAt).isEqualTo(
      java.time.Instant.parse("2000-01-01T00:00:00Z")
    );
    ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(
      Reservation.class
    );
    verify(reservations).save(saved.capture());
    assertThat(saved.getValue().program).isSameAs(program);
  }
}
