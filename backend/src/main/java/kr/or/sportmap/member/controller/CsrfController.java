package kr.or.sportmap.member.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
public class CsrfController {

  @GetMapping("/api/csrf")
  public CsrfToken token(CsrfToken token) {
    return token;
  }
}
