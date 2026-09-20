package kr.or.sportmap.program.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import kr.or.sportmap.facility.domain.Facility;
import kr.or.sportmap.facility.domain.FacilitySource;
import kr.or.sportmap.facility.repository.FacilityRepository;
import kr.or.sportmap.facility.repository.FacilitySourceRepository;
import kr.or.sportmap.program.domain.Program;
import kr.or.sportmap.program.repository.ProgramRepository;
import kr.or.sportmap.region.domain.Region;
import kr.or.sportmap.region.repository.RegionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/import/programs")
public class ProgramImportController {

  private static final String DATASET = "KS_PUBLIC_ALSFC_PROGRM_INFO";
  private final ProgramRepository programs;
  private final FacilityRepository facilities;
  private final FacilitySourceRepository sources;
  private final RegionRepository regions;
  private final String importKey;

  public ProgramImportController(
    ProgramRepository programs,
    FacilityRepository facilities,
    FacilitySourceRepository sources,
    RegionRepository regions,
    @Value("${app.import-key:}") String importKey
  ) {
    this.programs = programs;
    this.facilities = facilities;
    this.sources = sources;
    this.regions = regions;
    this.importKey = importKey;
  }

  /** 인증된 수집 요청을 받아 공공 프로그램과 해당 시설을 원천 키로 갱신한다. */
  @PostMapping
  @Transactional
  public Long upsert(
    @RequestHeader(value = "X-Import-Key", required = false) String key,
    @Valid @RequestBody ImportProgram body
  ) {
    if (
      importKey.isBlank() || !importKey.equals(key)
    ) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if (
      body.endsOn().isBefore(body.beginsOn())
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "invalid period"
    );
    Region region =
      body.regionCode() == null
        ? null
        : regions
            .findByCode(body.regionCode())
            .orElseGet(() ->
              regions.save(
                new Region(
                  body.regionCode(),
                  body.regionName() == null
                    ? body.regionCode()
                    : body.regionName(),
                  null
                )
              )
            );
    FacilitySource source = sources
      .findByDatasetCodeAndSourceKey(DATASET, body.facilitySourceKey())
      .orElse(null);
    Facility facility =
      source == null
        ? new Facility(body.facilityName(), region)
        : source.facility;
    facility.name = body.facilityName();
    facility.region = region;
    facility.roadAddress = body.address();
    facility.type = body.facilityType();
    facility.phone = body.phone();
    facility.website = body.registrationUrl();
    facility.latitude = body.latitude();
    facility.longitude = body.longitude();
    facility.publicFacility = true;
    // 원천 자료에는 실제 접수 가능 여부가 없다.
    facility.reservable = false;
    facilities.save(facility);
    if (source == null) sources.save(
      new FacilitySource(facility, DATASET, body.facilitySourceKey(), null)
    );
    Program program = programs
      .findBySourceKey(body.sourceKey())
      .orElseGet(Program::newImported);
    program.sourceKey = body.sourceKey();
    program.facility = facility;
    program.name = body.name();
    program.sportType = body.sportType();
    program.scheduleText = body.scheduleText();
    program.eligibility = body.eligibility();
    program.fee = body.fee();
    program.capacity = body.capacity();
    program.beginsOn = body.beginsOn();
    program.endsOn = body.endsOn();
    program.registrationUrl = body.registrationUrl();
    // 모집정원은 실시간 잔여석이 아니므로 자체 예약을 확정하지 않는다.
    program.bookingSupported = false;
    return programs.save(program).id;
  }

  public record ImportProgram(
    @NotBlank @Size(max = 64) String sourceKey,
    @NotBlank @Size(max = 200) String facilitySourceKey,
    @NotBlank @Size(max = 200) String facilityName,
    @Size(max = 500) String address,
    @Size(max = 20) String regionCode,
    @Size(max = 100) String regionName,
    @Size(max = 100) String facilityType,
    @Size(max = 50) String phone,
    BigDecimal latitude,
    BigDecimal longitude,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 100) String sportType,
    @Size(max = 500) String scheduleText,
    @Size(max = 500) String eligibility,
    @DecimalMin("0.0") BigDecimal fee,
    @Min(0) Integer capacity,
    @NotNull LocalDate beginsOn,
    @NotNull LocalDate endsOn,
    @Size(max = 500) String registrationUrl
  ) {}
}
