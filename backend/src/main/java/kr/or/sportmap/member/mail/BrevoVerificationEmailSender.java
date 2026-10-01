package kr.or.sportmap.member.mail;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@Component("brevoVerificationEmailSender")
public class BrevoVerificationEmailSender implements VerificationEmailSender {

  private static final Logger log = LoggerFactory.getLogger(
    BrevoVerificationEmailSender.class
  );
  private static final String SEND_URL =
    "https://api.brevo.com/v3/smtp/email";

  private final RestClient client;
  private final String apiKey;
  private final String from;
  private final String fromName;

  public BrevoVerificationEmailSender(
    RestClient.Builder builder,
    @Value("${app.mail.brevo-api-key:}") String apiKey,
    @Value("${app.mail.from:}") String from,
    @Value("${app.mail.from-name:SportsMate}") String fromName
  ) {
    this.client = builder.build();
    this.apiKey = apiKey;
    this.from = from;
    this.fromName = fromName;
  }

  @Override
  public void send(String email, String confirmationUrl) {
    if (apiKey.isBlank() || from.isBlank()) {
      log.error(
        "Brevo email configuration is incomplete (apiKeyConfigured={}, fromConfigured={})",
        !apiKey.isBlank(),
        !from.isBlank()
      );
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "이메일 발송 설정이 필요합니다. 관리자에게 문의해 주세요"
      );
    }

    String safeUrl = HtmlUtils.htmlEscape(confirmationUrl);
    Map<String, Object> request = Map.of(
      "sender",
      Map.of("name", fromName, "email", from),
      "to",
      List.of(Map.of("email", email)),
      "subject",
      "[SportsMate] 회원가입 이메일 인증",
      "textContent",
      "SportsMate 이메일 인증\n아래 링크를 10분 안에 열어 인증을 완료해 주세요.\n" +
      confirmationUrl +
      "\n요청하지 않았다면 무시하세요.",
      "htmlContent",
      "<div style=\"font-family:sans-serif;padding:32px;color:#173e32\">" +
      "<h2>SportsMate 이메일 인증</h2>" +
      "<p>아래 버튼을 눌러 인증을 완료해 주세요.</p>" +
      "<a href=\"" +
      safeUrl +
      "\" style=\"display:inline-block;padding:12px 18px;background:#173e32;color:#fff;text-decoration:none;border-radius:8px\">" +
      "이메일 인증 완료</a>" +
      "<p>발송 후 10분 동안 유효합니다. 요청하지 않았다면 무시하세요.</p>" +
      "</div>"
    );

    try {
      client
        .post()
        .uri(SEND_URL)
        .header("api-key", apiKey)
        .header("accept", "application/json")
        .body(request)
        .retrieve()
        .toBodilessEntity();
    } catch (RestClientResponseException error) {
      log.error(
        "Brevo email API rejected the request with HTTP status {}",
        error.getStatusCode().value()
      );
      throw unavailable();
    } catch (RestClientException error) {
      log.error(
        "Brevo email API request failed before receiving a response ({})",
        error.getClass().getSimpleName()
      );
      throw unavailable();
    }
  }

  private ResponseStatusException unavailable() {
    return new ResponseStatusException(
      HttpStatus.SERVICE_UNAVAILABLE,
      "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요"
    );
  }
}
