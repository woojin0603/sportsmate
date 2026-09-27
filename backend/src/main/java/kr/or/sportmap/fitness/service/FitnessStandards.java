package kr.or.sportmap.fitness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Snapshot of the published National Fitness 100 thresholds. Never interpolate between age bands. */
@Component
public class FitnessStandards {

  private final Catalog catalog;
  private final BodyCatalog bodyCatalog;

  public FitnessStandards(ObjectMapper mapper) throws IOException {
    try (
      InputStream in = new ClassPathResource(
        "fitness-standards.json"
      ).getInputStream();
      InputStream body = new ClassPathResource(
        "fitness-body-composition.json"
      ).getInputStream()
    ) {
      catalog = mapper.readValue(in, Catalog.class);
      bodyCatalog = mapper.readValue(body, BodyCatalog.class);
    }
    if (catalog.rows().size() != 132) throw new IllegalStateException(
      "Incomplete fitness standard catalog"
    );
  }

  public enum Stage {
    CHILD,
    TEEN,
    ADULT,
    SENIOR;

    public static Stage forAge(int age) {
      if (age >= 11 && age <= 12) return CHILD;
      if (age >= 13 && age <= 18) return TEEN;
      if (age >= 19 && age <= 64) return ADULT;
      if (age >= 65) return SENIOR;
      throw new IllegalArgumentException(
        "만 11세 이상만 A~D 참고 등급을 산출할 수 있습니다."
      );
    }
  }

  public record Catalog(
    String source,
    String retrievedOn,
    Map<String, List<String>> columns,
    List<ThresholdRow> rows
  ) {}

  public record ThresholdRow(
    Stage stage,
    String gender,
    int ageFrom,
    int ageTo,
    int grade,
    List<Double> thresholds
  ) {}

  public record BodyCatalog(String source, List<BodyRow> rows) {}

  public record BodyRow(
    Stage stage,
    String gender,
    int age,
    double bmiMax,
    double otherMax
  ) {}

  public Map<Integer, ThresholdRow> rows(Stage stage, String gender, int age) {
    Map<Integer, ThresholdRow> result = new HashMap<>();
    for (ThresholdRow row : catalog.rows())
      if (
        row.stage() == stage &&
        row.gender().equals(gender) &&
        age >= row.ageFrom() &&
        age <= row.ageTo()
      ) result.put(row.grade(), row);
    if (result.size() != 3) throw new IllegalStateException(
      "No complete standard for this age and gender"
    );
    return result;
  }

  public List<String> columns(Stage stage) {
    return catalog.columns().get(stage.name());
  }

  public Optional<BodyRow> bodyRow(Stage stage, String gender, int age) {
    return bodyCatalog
      .rows()
      .stream()
      .filter(
        row ->
          row.stage() == stage &&
          row.gender().equals(gender) &&
          row.age() == age
      )
      .findFirst();
  }

  public String source() {
    return catalog.source();
  }
}
