package kr.or.sportmap.fitness.controller;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 애플리케이션 기능을 구성한다. */
@RestControllerAdvice(assignableTypes = FitnessController.class)
public class FitnessErrorHandler {

  @ExceptionHandler({ IllegalArgumentException.class, IOException.class })
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public Map<String, String> invalid(Exception ex) {
    return Map.of(
      "message",
      ex.getMessage() == null ? "PDF를 처리하지 못했습니다." : ex.getMessage()
    );
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
  public Map<String, String> tooLarge() {
    return Map.of("message", "PDF는 10MB 이하만 업로드할 수 있습니다.");
  }
}
