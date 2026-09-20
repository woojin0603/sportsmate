package kr.or.sportmap.fitness.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import kr.or.sportmap.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FitnessScoringServiceTest {

  private FitnessScoringService scoring;
  private final Member adult = new Member(
    "테스트",
    "tester",
    "hash",
    LocalDate.of(1996, 1, 1),
    "tester@example.com",
    "010-1234-5678",
    Member.Gender.MALE
  );

  @BeforeEach
  void setUp() throws Exception {
    scoring = new FitnessScoringService(
      new FitnessStandards(new ObjectMapper())
    );
  }

  @Test
  void adultExactGradeOneThresholdIsA() {
    var result = scoring.evaluate(
      adult,
      LocalDate.of(2026, 1, 2),
      Map.of(
        "shuttle20",
        49.0,
        "grip",
        62.6,
        "sitUp",
        47.0,
        "flexibility",
        14.2,
        "longJump",
        219.0
      ),
      null
    );
    assertThat(result.status()).isEqualTo("READY");
    assertThat(result.grade()).isEqualTo("A");
    assertThat(result.ageAtTest()).isEqualTo(30);
    assertThat(result.score()).isEqualTo(100);
  }

  @Test
  void adultGradeTwoUsesAgeAndGenderBand() {
    var result = scoring.evaluate(
      adult,
      LocalDate.of(2026, 1, 2),
      Map.of(
        "shuttle20",
        40.0,
        "grip",
        57.2,
        "sitUp",
        41.0,
        "flexibility",
        9.4,
        "longJump",
        204.0
      ),
      null
    );
    assertThat(result.grade()).isEqualTo("B");
  }

  @Test
  void adultGradeThreeRequiresBodyComposition() {
    Map<String, Double> values = Map.of(
      "shuttle20",
      31.0,
      "grip",
      51.8,
      "sitUp",
      35.0,
      "flexibility",
      4.6,
      "longJump",
      100.0
    );
    assertThat(
      scoring.evaluate(adult, LocalDate.of(2026, 1, 2), values, null).status()
    ).isEqualTo("NEEDS_REVIEW");
    var withBmi = new java.util.HashMap<>(values);
    withBmi.put("bmi", 22.0);
    assertThat(
      scoring.evaluate(adult, LocalDate.of(2026, 1, 2), withBmi, null).grade()
    ).isEqualTo("C");
  }

  @Test
  void elderUsesDifferentMeasurements() {
    Member elder = new Member(
      "테스트",
      "elder",
      "hash",
      LocalDate.of(1960, 1, 1),
      "elder@example.com",
      "010-1234-5678",
      Member.Gender.FEMALE
    );
    var result = scoring.evaluate(
      elder,
      LocalDate.of(2026, 1, 2),
      Map.of(
        "walk2min",
        117.0,
        "grip",
        40.6,
        "chairStand",
        22.0,
        "flexibility",
        19.1,
        "upGo3m",
        5.6,
        "figure8",
        22.1
      ),
      null
    );
    assertThat(result.stage()).isEqualTo("SENIOR");
    assertThat(result.grade()).isEqualTo("A");
  }

  @Test
  void reportedGradeFiveMapsToDWithoutInventingMetricValues() {
    var result = scoring.evaluate(adult, LocalDate.of(2026, 1, 2), Map.of(), 5);
    assertThat(result.grade()).isEqualTo("D");
    assertThat(result.equivalentOfficialGrade()).isEqualTo(5);
    assertThat(result.metrics()).isEmpty();
  }

  @Test
  void implausibleOcrValueIsRejected() {
    assertThatThrownBy(() ->
      scoring.evaluate(
        adult,
        LocalDate.of(2026, 1, 2),
        Map.of("bmi", 900.0),
        null
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("BMI");
  }
}
