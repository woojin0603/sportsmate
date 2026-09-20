package kr.or.sportmap.reservation.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import kr.or.sportmap.reservation.domain.Reservation;
import kr.or.sportmap.reservation.service.ReservationService;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

  private final ReservationService service;

  public ReservationController(ReservationService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReservationResponse create(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody CreateRequest request
  ) {
    return ReservationResponse.of(
      service.create(
        jwt.getSubject(),
        request.programId(),
        request.startsAt(),
        request.endsAt()
      )
    );
  }

  @GetMapping
  public Page<ReservationResponse> mine(
    @AuthenticationPrincipal Jwt jwt,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST
    );
    return service
      .mine(
        jwt.getSubject(),
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
      )
      .map(ReservationResponse::of);
  }

  @GetMapping("/{id}")
  public ReservationResponse one(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable Long id
  ) {
    return ReservationResponse.of(service.one(jwt.getSubject(), id));
  }

  @PatchMapping("/{id}/cancel")
  public ReservationResponse cancel(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable Long id
  ) {
    return ReservationResponse.of(service.cancel(jwt.getSubject(), id));
  }

  public record CreateRequest(
    @NotNull Long programId,
    Instant startsAt,
    Instant endsAt
  ) {}

  public record ReservationResponse(
    Long id,
    Long programId,
    Instant startsAt,
    Instant endsAt,
    Reservation.Status status
  ) {
    static ReservationResponse of(Reservation r) {
      return new ReservationResponse(
        r.id,
        r.program.getId(),
        r.startsAt,
        r.endsAt,
        r.status
      );
    }
  }
}
