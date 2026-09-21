package kr.or.sportmap.member.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import kr.or.sportmap.member.config.SecurityConfig;
import kr.or.sportmap.member.repository.MemberRepository;
import kr.or.sportmap.member.service.PhoneVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
  controllers = { PhoneVerificationController.class, CsrfController.class },
  properties = "app.auth.jwt-secret=unit-test-secret-at-least-32-bytes-long"
)
@Import(SecurityConfig.class)
class PhoneVerificationControllerTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper mapper;

  @MockBean
  PhoneVerificationService service;

  @MockBean
  MemberRepository members;

  @Test
  void rejectsSendWithoutCsrf() throws Exception {
    mvc
      .perform(
        post("/api/users/phone/send")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"phoneNumber\":\"01012345678\"}")
      )
      .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  @Test
  void acceptsAnonymousRequestWithCsrfAndIgnoresStaleLoginCookie()
    throws Exception {
    var csrf = mvc.perform(get("/api/csrf")).andReturn().getResponse();
    String token = mapper
      .readTree(csrf.getContentAsString())
      .path("token")
      .asText();
    when(service.send(anyString(), anyString())).thenReturn(
      new PhoneVerificationService.SendResult(
        "request",
        Instant.now().plusSeconds(180),
        60,
        true,
        "123456"
      )
    );
    mvc
      .perform(
        post("/api/users/phone/send")
          .contentType(MediaType.APPLICATION_JSON)
          .cookie(
            csrf.getCookie("XSRF-TOKEN"),
            new Cookie("ACCESS_TOKEN", "stale-token")
          )
          .header("X-XSRF-TOKEN", token)
          .content("{\"phoneNumber\":\"01012345678\"}")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.developmentCode").value("123456"));
    verify(service).send(eq("01012345678"), anyString());
  }

  @Test
  void verifiesAnonymousCodeAndValidatesPayload() throws Exception {
    var csrf = mvc.perform(get("/api/csrf")).andReturn().getResponse();
    String token = mapper
      .readTree(csrf.getContentAsString())
      .path("token")
      .asText();
    when(service.verify("01012345678", "request", "123456")).thenReturn(
      new PhoneVerificationService.VerifyResult(
        "proof",
        Instant.now().plusSeconds(600)
      )
    );
    mvc
      .perform(
        post("/api/users/phone/verify")
          .contentType(MediaType.APPLICATION_JSON)
          .cookie(csrf.getCookie("XSRF-TOKEN"))
          .header("X-XSRF-TOKEN", token)
          .content(
            "{\"phoneNumber\":\"01012345678\",\"requestToken\":\"request\",\"code\":\"123456\"}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.verificationToken").value("proof"));
    mvc
      .perform(
        post("/api/users/phone/verify")
          .contentType(MediaType.APPLICATION_JSON)
          .cookie(csrf.getCookie("XSRF-TOKEN"))
          .header("X-XSRF-TOKEN", token)
          .content(
            "{\"phoneNumber\":\"01012345678\",\"requestToken\":\"request\",\"code\":\"abc\"}"
          )
      )
      .andExpect(status().isBadRequest());
  }
}
