package kr.or.sportmap.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배포 환경이 요청을 처리할 수 있는지 확인하는 가벼운 상태 API다. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

  /** 외부 상태 확인 도구가 사용할 최소 응답을 반환한다. */
  @GetMapping
  public Map<String, Object> health() {
    return Map.of("status", "UP", "checkedAt", Instant.now().toString());
  }
}
