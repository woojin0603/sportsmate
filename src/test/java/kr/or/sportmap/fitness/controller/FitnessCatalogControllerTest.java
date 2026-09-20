package kr.or.sportmap.fitness.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FitnessCatalogControllerTest {

  /** 공개 응답에는 원본의 개인 식별자와 측정값이 없어야 한다. */
  @Test
  void returnsAnonymizedPrescriptionsAndCenters() throws IOException {
    FitnessCatalogController controller = new FitnessCatalogController(
      new ObjectMapper()
    );
    var prescriptions = controller.prescriptions(30, "GENERAL", "F", "", "");
    assertFalse(prescriptions.isEmpty());
    assertTrue(
      prescriptions
        .stream()
        .allMatch(
          row -> row.path("count").asInt() >= 5 && !row.has("MBER_SEQ_NO_VALUE")
        )
    );
    assertFalse(controller.centers("서울").isEmpty());
    assertTrue(
      controller
        .centers("")
        .stream()
        .allMatch(row -> !row.path("phone").asText().isBlank())
    );
  }
}
