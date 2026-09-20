package kr.or.sportmap.facility.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PublicFacilityAreaServiceTest {

  /** 동일 시·군·구를 한 번만 조회하고 읍·면·동 결과를 정확히 분리한다. */
  @Test
  void filtersLocalityAndReusesProviderResponse() throws Exception {
    PublicFacilityApiService api = mock(PublicFacilityApiService.class);
    var response = new ObjectMapper().readTree(
      """
      {"response":{"body":{"totalCount":3,"items":{"item":[
        {"faci_nm":"A 체육관","addr_emd_nm":"역삼동"},
        {"faci_nm":"B 수영장","addr_emd_nm":"대치동"},
        {"faci_nm":"C 수영장","addr_emd_nm":"역삼동"}
      ]}}}}
      """
    );
    when(
      api.search(1, 100, null, null, null, null, "서울특별시", "강남구")
    ).thenReturn(response);
    PublicFacilityAreaService service = new PublicFacilityAreaService(api);

    assertEquals(2, service.localities("서울특별시", "강남구").size());
    var page = service.search(
      "서울특별시",
      "강남구",
      "역삼동",
      "수영",
      null,
      0,
      12
    );
    assertEquals(1, page.page().totalElements());
    assertEquals("C 수영장", page.content().get(0).path("faci_nm").asText());
    verify(api, times(1)).search(
      anyInt(),
      anyInt(),
      any(),
      any(),
      any(),
      any(),
      any(),
      any()
    );
  }
}
