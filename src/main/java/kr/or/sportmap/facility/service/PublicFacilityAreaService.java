package kr.or.sportmap.facility.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 공공 API가 제공하지 않는 읍·면·동 검색을 시설 주소 필드로 보완한다. */
@Service
public class PublicFacilityAreaService {

  private static final Duration CACHE_AGE = Duration.ofMinutes(10);
  private static final int PROVIDER_PAGE_SIZE = 100;
  private static final int MAX_PROVIDER_PAGES = 100;
  private final PublicFacilityApiService api;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public PublicFacilityAreaService(PublicFacilityApiService api) {
    this.api = api;
  }

  /** 시·도와 시·군·구에 속한 시설을 받아 실제 존재하는 읍·면·동 이름을 정렬한다. */
  public List<String> localities(String province, String city) {
    return rows(province, city)
      .stream()
      .map(row -> row.path("addr_emd_nm").asText("").trim())
      .filter(name -> !name.isBlank())
      .distinct()
      .sorted(Comparator.naturalOrder())
      .toList();
  }

  /** 선택한 읍·면·동에 해당하는 전체 시설을 서버에서 필터링한 뒤 페이지로 나눈다. */
  public FilteredPage search(
    String province,
    String city,
    String locality,
    String keyword,
    String category,
    int page,
    int size
  ) {
    if (page < 0 || size < 1 || size > 100) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "올바른 페이지를 입력해 주세요"
      );
    }
    List<JsonNode> matches = rows(province, city)
      .stream()
      .filter(row -> locality.equals(row.path("addr_emd_nm").asText("")))
      .filter(
        row ->
          keyword == null ||
          keyword.isBlank() ||
          row.path("faci_nm").asText("").contains(keyword.trim())
      )
      .filter(
        row ->
          category == null ||
          category.isBlank() ||
          category.equals(row.path("faci_gb_nm").asText(""))
      )
      .toList();
    int start = (int) Math.min((long) page * size, matches.size());
    int end = Math.min(start + size, matches.size());
    return new FilteredPage(
      matches.subList(start, end),
      new PageInfo(
        page,
        (int) Math.ceil((double) matches.size() / size),
        matches.size()
      )
    );
  }

  /** 동일 시·군·구 자료는 10분간 재사용해 공공 API 호출량을 줄인다. */
  private List<JsonNode> rows(String province, String city) {
    if (
      province == null || province.isBlank() || city == null || city.isBlank()
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "시·도와 시·군·구를 선택해 주세요"
      );
    }
    String key = province.trim() + "\u001f" + city.trim();
    CacheEntry cached = cache.get(key);
    if (
      cached != null &&
      cached.createdAt().plus(CACHE_AGE).isAfter(Instant.now())
    ) {
      return cached.rows();
    }
    synchronized (cache) {
      cached = cache.get(key);
      if (
        cached != null &&
        cached.createdAt().plus(CACHE_AGE).isAfter(Instant.now())
      ) {
        return cached.rows();
      }
      JsonNode first = api.search(
        1,
        PROVIDER_PAGE_SIZE,
        null,
        null,
        null,
        null,
        province,
        city
      );
      JsonNode body = first.path("response").path("body");
      int total = body.path("totalCount").asInt();
      int pages = (int) Math.ceil((double) total / PROVIDER_PAGE_SIZE);
      if (pages > MAX_PROVIDER_PAGES) {
        throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "결과가 너무 많습니다. 더 좁은 시·군·구를 입력해 주세요"
        );
      }
      List<JsonNode> result = new ArrayList<>(total);
      appendItems(result, body);
      for (int page = 2; page <= pages; page++) {
        JsonNode next = api.search(
          page,
          PROVIDER_PAGE_SIZE,
          null,
          null,
          null,
          null,
          province,
          city
        );
        appendItems(result, next.path("response").path("body"));
      }
      List<JsonNode> saved = List.copyOf(result);
      cache.put(key, new CacheEntry(Instant.now(), saved));
      return saved;
    }
  }

  /** 공공 API의 단건 객체 또는 배열 응답을 같은 목록 구조로 합친다. */
  private void appendItems(List<JsonNode> result, JsonNode body) {
    JsonNode items = body.path("items").path("item");
    if (items.isArray()) {
      items.forEach(result::add);
    } else if (items.isObject()) {
      result.add(items);
    }
  }

  private record CacheEntry(Instant createdAt, List<JsonNode> rows) {}

  public record PageInfo(int number, int totalPages, int totalElements) {}

  public record FilteredPage(List<JsonNode> content, PageInfo page) {}
}
