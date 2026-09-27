package kr.or.sportmap.facility.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import kr.or.sportmap.facility.domain.*;
import kr.or.sportmap.facility.repository.*;
import kr.or.sportmap.region.domain.Region;
import kr.or.sportmap.region.repository.RegionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/import/facilities")
public class FacilityImportController {

  private final FacilityRepository facilities;
  private final FacilitySourceRepository sources;
  private final RegionRepository regions;
  private final String importKey;

  public FacilityImportController(
    FacilityRepository facilities,
    FacilitySourceRepository sources,
    RegionRepository regions,
    @Value("${app.import-key:}") String importKey
  ) {
    this.facilities = facilities;
    this.sources = sources;
    this.regions = regions;
    this.importKey = importKey;
  }

  @PostMapping
  @Transactional
  public Long upsert(
    @RequestHeader(value = "X-Import-Key", required = false) String key,
    @Valid @RequestBody ImportFacility body
  ) {
    if (
      importKey.isBlank() || !importKey.equals(key)
    ) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    FacilitySource source = sources
      .findByDatasetCodeAndSourceKey(body.datasetCode(), body.sourceKey())
      .orElse(null);
    Facility f =
      source == null ? new Facility(body.name(), null) : source.facility;
    Region r =
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
    f.name = body.name();
    f.region = r;
    f.roadAddress = body.roadAddress();
    f.type = body.type();
    f.latitude = body.latitude();
    f.longitude = body.longitude();
    f.phone = body.phone();
    f.publicFacility = body.publicFacility();
    f.reservable = body.reservable();
    facilities.save(f);
    if (source == null) sources.save(
      new FacilitySource(
        f,
        body.datasetCode(),
        body.sourceKey(),
        body.rawJson()
      )
    );
    else {
      source.rawJson = body.rawJson();
      source.fetchedAt = java.time.Instant.now();
    }
    return f.id;
  }

  public record ImportFacility(
    @NotBlank String datasetCode,
    @NotBlank String sourceKey,
    @NotBlank String name,
    String regionCode,
    String regionName,
    String roadAddress,
    String type,
    BigDecimal latitude,
    BigDecimal longitude,
    String phone,
    Boolean publicFacility,
    Boolean reservable,
    String rawJson
  ) {}
}
