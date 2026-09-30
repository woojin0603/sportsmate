package kr.or.sportmap.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배포 환경이 요청을 처리할 수 있는지 확인하는 가벼운 상태 API다. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

  private final JdbcTemplate jdbc;

  public HealthController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** 서버와 DB가 모두 요청을 처리할 수 있을 때만 UP을 반환한다. */
  @GetMapping
  public ResponseEntity<Map<String, Object>> health() {
    String checkedAt = Instant.now().toString();
    try {
      Integer result = jdbc.queryForObject("select 1", Integer.class);
      if (result != null && result == 1) {
        return ResponseEntity.ok(
          Map.of("status", "UP", "database", "UP", "checkedAt", checkedAt)
        );
      }
    } catch (RuntimeException ignored) {
      // 상태 API에는 DB 예외나 접속 정보를 노출하지 않는다.
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
      Map.of("status", "DOWN", "database", "DOWN", "checkedAt", checkedAt)
    );
  }
}
