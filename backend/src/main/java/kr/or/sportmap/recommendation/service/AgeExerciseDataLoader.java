package kr.or.sportmap.recommendation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import kr.or.sportmap.recommendation.domain.ExerciseRecommendation;
import kr.or.sportmap.recommendation.repository.ExerciseRecommendationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 제공된 연령별 운동정보 5,040행을 처음 실행할 때 적재한다. */
@Component
public class AgeExerciseDataLoader implements ApplicationRunner {

  private static final String DATASET = "AGE_EXERCISE_2026";
  private final ExerciseRecommendationRepository repository;
  private final ObjectMapper mapper;

  public AgeExerciseDataLoader(
    ExerciseRecommendationRepository repository,
    ObjectMapper mapper
  ) {
    this.repository = repository;
    this.mapper = mapper;
  }

  /** 이미 적재한 데이터는 중복으로 삽입하지 않는다. */
  @Override
  public void run(ApplicationArguments args) throws IOException {
    if (repository.countByDatasetCode(DATASET) > 0) return;
    JsonNode rows;
    try (
      InputStream input = new ClassPathResource(
        "age-exercise-recommendations.json"
      ).getInputStream()
    ) {
      rows = mapper.readTree(input);
    }
    List<ExerciseRecommendation> items = new ArrayList<>();
    int rowNumber = 0;
    for (JsonNode row : rows) {
      int decade = ageStart(row.path("AGRDE_FLAG_NM").asText());
      items.add(
        ExerciseRecommendation.fromDataset(
          decade,
          decade == 70 ? 120 : decade + 9,
          row.path("RECOMEND_MVM_NM").asText(),
          row.path("BMI_IDEX_GRAD_NM").asText(),
          row.path("MBER_SEXDSTN_FLAG_CD").asText(),
          row.path("COAW_FLAG_NM").asText(),
          row.path("SPORTS_STEP_NM").asText(),
          row.path("FLAG_ACCTO_RECOMEND_MVM_RANK_CO").asInt(),
          String.valueOf(++rowNumber)
        )
      );
    }
    repository.saveAll(items);
  }

  /** '10대'부터 '70대 이상'까지를 조회 가능한 연령 범위로 변환한다. */
  private int ageStart(String label) {
    return Integer.parseInt(label.substring(0, 2));
  }
}
