package kr.or.sportmap.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** React 화면의 직접 접속과 새로고침을 처리한다. */
@Controller
public class SpaController {

  /** 화면 경로를 Vite가 생성한 단일 진입점으로 전달한다. */
  @GetMapping({
    "/",
    "/live-facilities",
    "/facilities/{id}",
    "/programs",
    "/programs/{id}",
    "/recommendations",
    "/fitness",
    "/fitness-centers",
    "/reservations",
    "/community",
    "/community/{kind}/{id}",
    "/account",
    "/admin",
  })
  public String index() {
    return "forward:/index.html";
  }
}
