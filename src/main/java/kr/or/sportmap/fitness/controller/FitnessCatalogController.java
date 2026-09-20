package kr.or.sportmap.fitness.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 개인 식별자를 제거한 체력측정 운동처방 사례와 센터 정보를 제공한다. */
@RestController
@RequestMapping("/api/fitness")
public class FitnessCatalogController {

  private final JsonNode catalog;

  public FitnessCatalogController(ObjectMapper mapper) throws IOException {
    try (
      InputStream input = new ClassPathResource(
        "fitness-prescription-catalog.json"
      ).getInputStream()
    ) {
      catalog = mapper.readTree(input);
    }
  }

  /** 연령대·성별·출처에 맞는 빈도 상위 운동처방 사례를 조회한다. */
  @GetMapping("/prescriptions")
  public List<JsonNode> prescriptions(
    @RequestParam int age,
    @RequestParam(defaultValue = "GENERAL") String source,
    @RequestParam(defaultValue = "") String sex,
    @RequestParam(defaultValue = "") String grade,
    @RequestParam(defaultValue = "") String disabilityType
  ) {
    if (age < 10 || age > 120) throw new IllegalArgumentException(
      "age must be 10..120"
    );
    if (
      !List.of("GENERAL", "LOCAL", "DISABILITY").contains(source)
    ) throw new IllegalArgumentException("invalid source");
    if (
      source.equals("DISABILITY") && disabilityType.isBlank()
    ) throw new IllegalArgumentException(
      "disabilityType is required for disability data"
    );
    String ageBand = age >= 70 ? "70대 이상" : (age / 10) * 10 + "대";
    return StreamSupport.stream(
      catalog.path("prescriptions").spliterator(),
      false
    )
      .filter(row -> source.equals(row.path("source").asText()))
      .filter(row -> ageBand.equals(row.path("ageBand").asText()))
      .filter(row -> sex.isBlank() || sex.equals(row.path("sex").asText()))
      .filter(
        row -> grade.isBlank() || grade.equals(row.path("grade").asText())
      )
      .filter(
        row ->
          disabilityType.isBlank() ||
          disabilityType.equals(row.path("disabilityType").asText())
      )
      .sorted(
        Comparator.comparingInt((JsonNode row) ->
          row.path("count").asInt()
        ).reversed()
      )
      .limit(12)
      .toList();
  }

  /** 체력측정센터 이름이나 지역명으로 센터 연락처를 찾는다. */
  @GetMapping("/centers")
  public List<JsonNode> centers(
    @RequestParam(defaultValue = "") String keyword
  ) {
    String search = keyword.trim().toLowerCase();
    return StreamSupport.stream(catalog.path("centers").spliterator(), false)
      .filter(
        row ->
          search.isBlank() ||
          (
            row.path("name").asText() +
            " " +
            row.path("province").asText() +
            " " +
            row.path("district").asText() +
            " " +
            row.path("phone").asText()
          )
            .toLowerCase()
            .contains(search)
      )
      .sorted(
        Comparator.comparing(
          (JsonNode row) ->
            row.path("province").asText() + row.path("name").asText()
        )
      )
      .limit(100)
      .toList();
  }
}
