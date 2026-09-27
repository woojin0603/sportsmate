package kr.or.sportmap.fitness.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FitnessPdfReaderTest {

  private final FitnessPdfReader reader = new FitnessPdfReader("tesseract");

  @Test
  void extractsDateGradeAndMeasurementsWithoutConfusingAbsoluteGrip() {
    var parsed = reader.parseExtractedText(
      "국민체력100 체력측정 결과지\n측정일: 2026.09.13\n" +
        "체력인증 등급: 2등급\n상대악력 (%) 57.2\n교차윗몸일으키기 (회) 41\n" +
        "20m 왕복오래달리기 (회) 40\n앉아윗몸앞으로굽히기 (cm) 9.4\nBMI 22.1",
      false
    );
    assertThat(parsed.measuredOn()).isEqualTo(LocalDate.of(2026, 9, 13));
    assertThat(parsed.reportedOfficialGrade()).isEqualTo(2);
    assertThat(parsed.values())
      .containsEntry("grip", 57.2)
      .containsEntry("shuttle20", 40.0)
      .containsEntry("sitUp", 41.0)
      .containsEntry("bmi", 22.1);
  }

  @Test
  void incompleteTextDoesNotInventGrade() {
    var parsed = reader.parseExtractedText(
      "측정일: 2026-09-13\n악력 35kg",
      false
    );
    assertThat(parsed.reportedOfficialGrade()).isNull();
    assertThat(parsed.values()).doesNotContainKey("grip");
    assertThat(parsed.warnings()).anyMatch(message ->
      message.contains("읽지 못")
    );
  }

  @Test
  void readsKoreanSyllablesSeparatedByOcr() {
    var parsed = reader.parseExtractedText(
      "측 정 일 2026-09-13\n상 대 악 력 62.6",
      true
    );
    assertThat(parsed.measuredOn()).isEqualTo(LocalDate.of(2026, 9, 13));
    assertThat(parsed.values()).containsEntry("grip", 62.6);
  }
}
