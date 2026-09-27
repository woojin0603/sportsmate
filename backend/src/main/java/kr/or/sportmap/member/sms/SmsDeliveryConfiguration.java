package kr.or.sportmap.member.sms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class SmsDeliveryConfiguration {

  @Bean("mockSmsSender")
  SmsSender mockSmsSender() {
    // No SMS, console output, or external request. The guarded API supplies the demo code.
    return (phone, code) -> {};
  }

  @Bean("disabledSmsSender")
  SmsSender disabledSmsSender() {
    return (phone, code) -> {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "문자 인증 서비스가 설정되지 않았습니다"
      );
    };
  }
}
