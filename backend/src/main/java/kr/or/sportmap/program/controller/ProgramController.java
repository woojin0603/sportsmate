package kr.or.sportmap.program.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import kr.or.sportmap.facility.controller.FacilityController;
import kr.or.sportmap.program.domain.Program;
import kr.or.sportmap.program.repository.ProgramRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/programs")
public class ProgramController {

  private final ProgramRepository repository;

  public ProgramController(ProgramRepository repository) {
    this.repository = repository;
  }

  /** 시설 또는 프로그램명으로 적재된 강좌를 검색한다. */
  @GetMapping
  public Page<ProgramResponse> list(
    @RequestParam(required = false) Long facilityId,
    @RequestParam(defaultValue = "") String keyword,
    @RequestParam(defaultValue = "") String region,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST
    );
    var paging = PageRequest.of(page, size);
    if (facilityId != null) {
      return repository
        .findByFacilityId(facilityId, paging)
        .map(ProgramResponse::from);
    }
    return repository
      .search(keyword.trim(), region.trim(), paging)
      .map(ProgramResponse::from);
  }

  /** 프로그램 시설 주소를 시·도, 시·군·구, 하위 구역으로 나눠 필터 선택지로 반환한다. */
  @GetMapping("/regions")
  public List<ProgramRegionResponse> regions() {
    return repository
      .findAllForRegionOptions()
      .stream()
      .map(program -> ProgramRegionResponse.from(program))
      .filter(option -> option.province() != null)
      .distinct()
      .sorted((left, right) ->
        left.displayName().compareTo(right.displayName())
      )
      .toList();
  }

  /** 선택한 프로그램의 일정·요금·신청 안내를 반환한다. */
  @GetMapping("/{id}")
  public ProgramResponse one(@PathVariable Long id) {
    return repository
      .findById(id)
      .map(ProgramResponse::from)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  public record ProgramResponse(
    Long id,
    Long facilityId,
    String operatingOrganization,
    String regionName,
    String name,
    String sportType,
    String scheduleText,
    String eligibility,
    BigDecimal fee,
    Integer capacity,
    Boolean bookingSupported,
    LocalDate beginsOn,
    LocalDate endsOn,
    String registrationUrl
  ) {
    static ProgramResponse from(Program p) {
      return new ProgramResponse(
        p.id,
        p.facility == null ? null : p.facility.getId(),
        p.facility == null ? null : p.facility.name,
        p.facility == null
          ? null
          : FacilityController.FacilityResponse.displayRegion(p.facility),
        p.name,
        p.sportType,
        p.scheduleText,
        p.eligibility,
        p.fee,
        p.capacity,
        p.bookingSupported,
        p.beginsOn,
        p.endsOn,
        p.registrationUrl
      );
    }
  }

  public record ProgramRegionResponse(
    String province,
    String city,
    String locality,
    String displayName
  ) {
    /** 시설 주소의 앞부분에서 순서대로 행정구역만 추출한다. */
    static ProgramRegionResponse from(Program program) {
      String display = FacilityController.FacilityResponse.displayRegion(
        program.facility
      );
      if (display == null || display.isBlank()) {
        return new ProgramRegionResponse(null, null, null, "");
      }
      String[] parts = display.split("\\s+");
      return new ProgramRegionResponse(
        parts.length > 0 ? parts[0] : null,
        parts.length > 1 ? parts[1] : null,
        parts.length > 2
          ? String.join(
              " ",
              java.util.Arrays.copyOfRange(parts, 2, parts.length)
            )
          : null,
        display
      );
    }
  }
}
