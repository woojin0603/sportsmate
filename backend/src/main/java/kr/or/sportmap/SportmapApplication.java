package kr.or.sportmap;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/** 애플리케이션 기능을 구성한다. */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class SportmapApplication {

  public static void main(String[] args) {
    SpringApplication.run(SportmapApplication.class, args);
  }
}
