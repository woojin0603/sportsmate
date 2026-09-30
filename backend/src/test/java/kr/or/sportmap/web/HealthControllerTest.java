package kr.or.sportmap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final HealthController controller = new HealthController(jdbc);

  @Test
  void reportsUpWhenDatabaseResponds() {
    when(jdbc.queryForObject("select 1", Integer.class)).thenReturn(1);

    var response = controller.health();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("UP", response.getBody().get("status"));
    assertEquals("UP", response.getBody().get("database"));
  }

  @Test
  void reportsDownWithoutLeakingDatabaseError() {
    when(jdbc.queryForObject("select 1", Integer.class)).thenThrow(
      new IllegalStateException("jdbc:mysql://secret-host/private")
    );

    var response = controller.health();

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    assertEquals("DOWN", response.getBody().get("status"));
    assertEquals("DOWN", response.getBody().get("database"));
  }
}
