package kr.or.sportmap.facility.service;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublicFacilityApiServiceTest {

  @Test
  void buildsSpecifiedParameterNamesWithoutDoubleEncoding() {
    URI uri = PublicFacilityApiService.buildUri(
      "https://example.org/api",
      "abc%2Fdef%2Bghi",
      1,
      10,
      Map.of("faci_nm", "센터럴 피트니스", "faci_gb_nm", "신고")
    );
    String query = uri.getRawQuery();
    assertTrue(query.contains("serviceKey=abc%2Fdef%2Bghi"));
    assertTrue(query.contains("pageNo=1"));
    assertTrue(query.contains("numOfRows=10"));
    assertTrue(query.contains("resultType=JSON"));
    assertTrue(
      query.contains(
        "faci_nm=%EC%84%BC%ED%84%B0%EB%9F%B4+%ED%94%BC%ED%8A%B8%EB%8B%88%EC%8A%A4"
      )
    );
    assertTrue(query.contains("faci_gb_nm=%EC%8B%A0%EA%B3%A0"));
    assertFalse(query.contains("fcob_nm="));
  }
}
