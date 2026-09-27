package kr.or.sportmap.facility.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import kr.or.sportmap.facility.service.PublicFacilityApiService;
import kr.or.sportmap.facility.service.PublicFacilityAreaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/facilities/external")
public class PublicFacilityApiController {

  private final PublicFacilityApiService service;
  private final PublicFacilityAreaService areas;

  public PublicFacilityApiController(
    PublicFacilityApiService service,
    PublicFacilityAreaService areas
  ) {
    this.service = service;
    this.areas = areas;
  }

  /** 시·도와 시·군·구의 공공시설 자료에서 실제 읍·면·동 후보를 찾는다. */
  @GetMapping("/localities")
  public List<String> localities(
    @RequestParam(name = "cp_nm") String province,
    @RequestParam(name = "cpb_nm") String city
  ) {
    return areas.localities(province, city);
  }

  /** 공공 API가 제공하지 않는 읍·면·동 조건으로 전체 시설을 필터링한다. */
  @GetMapping("/by-locality")
  public PublicFacilityAreaService.FilteredPage byLocality(
    @RequestParam(name = "cp_nm") String province,
    @RequestParam(name = "cpb_nm") String city,
    @RequestParam(name = "addr_emd_nm") String locality,
    @RequestParam(name = "faci_nm", required = false) String keyword,
    @RequestParam(name = "faci_gb_nm", required = false) String category,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "12") int size
  ) {
    return areas.search(
      province,
      city,
      locality,
      keyword,
      category,
      page,
      size
    );
  }

  /** 요청변수를 공공데이터포털 시설 검색 서비스에 전달하고 원본 JSON을 반환한다. */
  @GetMapping
  public JsonNode search(
    @RequestParam(defaultValue = "1") int pageNo,
    @RequestParam(defaultValue = "10") int numOfRows,
    @RequestParam(name = "faci_nm", required = false) String faciNm,
    @RequestParam(name = "faci_gb_nm", required = false) String faciGbNm,
    @RequestParam(name = "fcob_nm", required = false) String fcobNm,
    @RequestParam(name = "ftype_nm", required = false) String ftypeNm,
    @RequestParam(name = "cp_nm", required = false) String cpNm,
    @RequestParam(name = "cpb_nm", required = false) String cpbNm
  ) {
    return service.search(
      pageNo,
      numOfRows,
      faciNm,
      faciGbNm,
      fcobNm,
      ftypeNm,
      cpNm,
      cpbNm
    );
  }
}
