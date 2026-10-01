package kr.or.sportmap.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import kr.or.sportmap.member.mail.BrevoVerificationEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class BrevoVerificationEmailSenderTest {

  @Test
  void rejectsMissingApiConfiguration() {
    var sender = new BrevoVerificationEmailSender(
      RestClient.builder(),
      "",
      "",
      "SportsMate"
    );

    var error = assertThrows(ResponseStatusException.class, () ->
      sender.send("demo@example.com", "https://example.com/confirm")
    );

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
  }

  @Test
  void sendsVerificationEmailThroughBrevoApi() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer
      .bindTo(builder)
      .build();
    var sender = new BrevoVerificationEmailSender(
      builder,
      "secret-api-key",
      "sender@example.com",
      "SportsMate"
    );
    server
      .expect(requestTo("https://api.brevo.com/v3/smtp/email"))
      .andExpect(method(HttpMethod.POST))
      .andExpect(header("api-key", "secret-api-key"))
      .andExpect(jsonPath("$.sender.email").value("sender@example.com"))
      .andExpect(jsonPath("$.sender.name").value("SportsMate"))
      .andExpect(jsonPath("$.to[0].email").value("recipient@example.com"))
      .andExpect(jsonPath("$.htmlContent").exists())
      .andRespond(withSuccess("{\"messageId\":\"test\"}", MediaType.APPLICATION_JSON));

    sender.send(
      "recipient@example.com",
      "https://example.com/api/users/email/confirm?token=test"
    );

    server.verify();
  }
}
