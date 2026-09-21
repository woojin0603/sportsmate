package kr.or.sportmap.member.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import kr.or.sportmap.member.config.SecurityConfig;
import kr.or.sportmap.member.repository.MemberRepository;
import kr.or.sportmap.member.service.EmailVerificationService;
import kr.or.sportmap.member.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
  controllers = { MemberController.class, CsrfController.class },
  properties = "app.auth.jwt-secret=unit-test-secret-at-least-32-bytes-long"
)
@Import(SecurityConfig.class)
class EmailVerificationControllerTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper mapper;

  @MockBean
  EmailVerificationService service;

  @MockBean
  MemberService members;

  @MockBean
  MemberRepository repository;

  @Test
  void sendRequiresCsrfAndReturnsUncachedMockLink() throws Exception {
    mvc
      .perform(
        post("/api/users/email/send")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"demo@example.com\"}")
      )
      .andExpect(status().isForbidden());
    verifyNoInteractions(service);
    var csrf = mvc.perform(get("/api/csrf")).andReturn().getResponse();
    String token = mapper
      .readTree(csrf.getContentAsString())
      .path("token")
      .asText();
    when(service.sendLink(anyString())).thenReturn(
      new EmailVerificationService.SendResult(
        "request",
        Instant.now().plusSeconds(600),
        60,
        true,
        "/api/users/email/confirm?token=test"
      )
    );
    mvc
      .perform(
        post("/api/users/email/send")
          .contentType(MediaType.APPLICATION_JSON)
          .cookie(csrf.getCookie("XSRF-TOKEN"))
          .header("X-XSRF-TOKEN", token)
          .content("{\"email\":\"demo@example.com\"}")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.mock").value(true))
      .andExpect(
        jsonPath("$.developmentConfirmationUrl").value(
          "/api/users/email/confirm?token=test"
        )
      );
  }

  @Test
  void confirmIsPublicAndDoesNotCacheOrLeakReferrer() throws Exception {
    mvc
      .perform(get("/api/users/email/confirm").param("token", "test"))
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(header().string("Referrer-Policy", "no-referrer"));
    verify(service).confirm("test");
  }

  @Test
  void statusProofIsNotCached() throws Exception {
    var csrf = mvc.perform(get("/api/csrf")).andReturn().getResponse();
    String token = mapper
      .readTree(csrf.getContentAsString())
      .path("token")
      .asText();
    when(service.status("demo@example.com", "request")).thenReturn(
      new EmailVerificationService.VerificationStatus(true, "proof")
    );
    mvc
      .perform(
        post("/api/users/email/status")
          .contentType(MediaType.APPLICATION_JSON)
          .cookie(csrf.getCookie("XSRF-TOKEN"))
          .header("X-XSRF-TOKEN", token)
          .content(
            "{\"email\":\"demo@example.com\",\"requestToken\":\"request\"}"
          )
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.verificationToken").value("proof"));
  }
}
