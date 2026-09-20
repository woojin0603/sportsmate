package kr.or.sportmap.facility.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 도메인 규칙과 외부 연동 작업을 처리한다. */
@Service
public class PublicFacilityApiService {

  private final HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .build();
  private final ObjectMapper mapper;
  private final String endpoint;
  private final String serviceKey;

  public PublicFacilityApiService(
    ObjectMapper mapper,
    @Value("${app.public-facility.url:}") String endpoint,
    @Value("${app.public-facility.service-key:}") String serviceKey
  ) {
    this.mapper = mapper;
    this.endpoint = endpoint;
    this.serviceKey = serviceKey;
  }

  /** 인증키와 검색 조건으로 공공데이터포털을 호출하고 응답 코드까지 검증한다. */
  public JsonNode search(
    int pageNo,
    int numOfRows,
    String faciNm,
    String faciGbNm,
    String fcobNm,
    String ftypeNm,
    String cpNm,
    String cpbNm
  ) {
    if (
      endpoint.isBlank() || serviceKey.isBlank()
    ) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "공공데이터 API URL/키가 설정되지 않았습니다"
    );
    if (
      !endpoint.startsWith("https://") && !endpoint.startsWith("http://")
    ) throw new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "공공데이터 API URL이 잘못되었습니다"
    );
    if (
      pageNo < 1 || numOfRows < 1 || numOfRows > 100
    ) throw new ResponseStatusException(
      HttpStatus.BAD_REQUEST,
      "pageNo >= 1, numOfRows 1..100"
    );
    try {
      URI uri = buildUri(
        endpoint,
        serviceKey,
        pageNo,
        numOfRows,
        Map.of(
          "faci_nm",
          value(faciNm),
          "faci_gb_nm",
          value(faciGbNm),
          "fcob_nm",
          value(fcobNm),
          "ftype_nm",
          value(ftypeNm),
          "cp_nm",
          value(cpNm),
          "cpb_nm",
          value(cpbNm)
        )
      );
      HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(15))
        .header("Accept", "application/json")
        .GET()
        .build();
      HttpResponse<String> response = client.send(
        request,
        HttpResponse.BodyHandlers.ofString()
      );
      if (
        response.statusCode() < 200 || response.statusCode() >= 300
      ) throw new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "공공데이터 API HTTP " + response.statusCode()
      );
      JsonNode body = mapper.readTree(response.body());
      JsonNode header = body.path("response").path("header");
      if (
        header.isMissingNode() || header.path("resultCode").isMissingNode()
      ) throw new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "공공데이터 API 응답 형식 오류"
      );
      String resultCode = header.path("resultCode").asText();
      if (
        !"00".equals(resultCode) && !"0".equals(resultCode)
      ) throw new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "공공데이터 API 오류 코드 " + resultCode
      );
      return body;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "공공데이터 API 연결 실패"
      );
    }
  }

  /** 인코딩된 인증키와 선택 검색값을 중복 인코딩 없이 요청 URL에 담는다. */
  static URI buildUri(
    String endpoint,
    String encodedOrDecodedKey,
    int pageNo,
    int numOfRows,
    Map<String, String> filters
  ) {
    String decodedKey = encodedOrDecodedKey.contains("%")
      ? URLDecoder.decode(encodedOrDecodedKey, StandardCharsets.UTF_8)
      : encodedOrDecodedKey;
    List<String> pairs = new ArrayList<>();
    pairs.add(pair("serviceKey", decodedKey));
    pairs.add(pair("pageNo", Integer.toString(pageNo)));
    pairs.add(pair("numOfRows", Integer.toString(numOfRows)));
    pairs.add(pair("resultType", "JSON"));
    for (String name : List.of(
      "faci_nm",
      "faci_gb_nm",
      "fcob_nm",
      "ftype_nm",
      "cp_nm",
      "cpb_nm"
    )) {
      String value = filters.get(name);
      if (value != null && !value.isBlank()) pairs.add(
        pair(name, value.trim())
      );
    }
    String separator = endpoint.contains("?") ? "&" : "?";
    return URI.create(endpoint + separator + String.join("&", pairs));
  }

  private static String pair(String key, String value) {
    return (
      URLEncoder.encode(key, StandardCharsets.UTF_8) +
      "=" +
      URLEncoder.encode(value, StandardCharsets.UTF_8)
    );
  }

  private static String value(String input) {
    return input == null ? "" : input;
  }
}
