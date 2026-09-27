package kr.or.sportmap.member.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigTest {

  /** 서버 재시작 전 JWT 쿠키가 남아도 공개 로그인 요청은 통과시킨다. */
  @Test
  void ignoresStaleCookieOnPublicAuthPaths() {
    var resolver = new SecurityConfig().bearerTokenResolver();
    var login = new MockHttpServletRequest("POST", "/api/users/login");
    login.setCookies(new Cookie("ACCESS_TOKEN", "stale-token"));
    assertNull(resolver.resolve(login));

    var signup = new MockHttpServletRequest("POST", "/api/users/signup");
    signup.setCookies(new Cookie("ACCESS_TOKEN", "stale-token"));
    assertNull(resolver.resolve(signup));

    var emailSend = new MockHttpServletRequest("POST", "/api/users/email/send");
    emailSend.setCookies(new Cookie("ACCESS_TOKEN", "stale-token"));
    assertNull(resolver.resolve(emailSend));

    var home = new MockHttpServletRequest("GET", "/");
    home.setCookies(new Cookie("ACCESS_TOKEN", "stale-token"));
    assertNull(resolver.resolve(home));

    var mypage = new MockHttpServletRequest("GET", "/api/users/mypage");
    mypage.setCookies(new Cookie("ACCESS_TOKEN", "valid-token"));
    assertEquals("valid-token", resolver.resolve(mypage));
  }
}
