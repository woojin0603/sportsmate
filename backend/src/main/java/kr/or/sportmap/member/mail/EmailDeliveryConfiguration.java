package kr.or.sportmap.member.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class EmailDeliveryConfiguration {

  @Bean("mockVerificationEmailSender")
  VerificationEmailSender mockSender() {
    return (email, url) -> {};
  }

  @Bean("disabledVerificationEmailSender")
  VerificationEmailSender disabledSender() {
    return (email, url) -> {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "이메일 발송 서비스가 설정되지 않았습니다"
      );
    };
  }
}
