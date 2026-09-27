package kr.or.sportmap.facility.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kr.or.sportmap.facility.domain.Facility;
import kr.or.sportmap.facility.repository.FacilityRepository;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

  private final FacilityRepository repository;

  public FacilityController(FacilityRepository repository) {
    this.repository = repository;
  }

  /** 적재된 시설을 이름·지역·공공 여부로 검색해 페이지 단위로 반환한다. */
  @GetMapping
  public Page<FacilityResponse> list(
    @RequestParam(required = false) String keyword,
    @RequestParam(required = false) String regionCode,
    @RequestParam(required = false) Boolean publicOnly,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "invalid pagination"
    );
    return repository
      .search(keyword, regionCode, publicOnly, PageRequest.of(page, size))
      .map(FacilityResponse::from);
  }

  /** 선택한 시설의 위치·유형·연락처를 반환한다. */
  @GetMapping("/{id}")
  public FacilityResponse one(@PathVariable Long id) {
    return repository
      .findById(id)
      .map(FacilityResponse::from)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  public record FacilityResponse(
    Long id,
    String name,
    String regionCode,
    String regionName,
    String roadAddress,
    String type,
    BigDecimal latitude,
    BigDecimal longitude,
    String phone,
    String website,
    Boolean publicFacility,
    Boolean reservable
  ) {
    /** 주소에 있는 시·도부터 하위 행정구역까지 표시하고, 주소가 없으면 지역 계층을 사용한다. */
    public static String displayRegion(Facility facility) {
      if (facility.roadAddress != null && !facility.roadAddress.isBlank()) {
        String[] address = facility.roadAddress.trim().split("\\s+");
        if (address.length >= 2 && address[0].matches(".*[시도]")) {
          List<String> parts = new ArrayList<>();
          for (String part : address) {
            if (!part.matches(".*(시|군|구|읍|면|동|동[0-9]+가)")) break;
            parts.add(part);
          }
          if (parts.size() >= 2) return String.join(" ", parts);
        }
      }
      List<String> parts = new ArrayList<>();
      for (
        var region = facility.region;
        region != null;
        region = region.parent
      ) {
        if (region.name != null && !region.name.equals(region.code)) parts.add(
          region.name
        );
      }
      Collections.reverse(parts);
      return parts.isEmpty() ? null : String.join(" ", parts);
    }

    static FacilityResponse from(Facility f) {
      return new FacilityResponse(
        f.id,
        f.name,
        f.region == null ? null : f.region.code,
        displayRegion(f),
        f.roadAddress,
        f.type,
        f.latitude,
        f.longitude,
        f.phone,
        f.website,
        f.publicFacility,
        f.reservable
      );
    }
  }
}
