package kr.or.sportmap.member.sms;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SmsGateway {

  private final SmsSender sender;
  private final boolean mock;
  private final boolean enabled;

  public SmsGateway(
    Map<String, SmsSender> senders,
    @Value("${app.sms.mode:disabled}") String mode,
    Environment environment
  ) {
    mock = "mock".equals(mode);
    enabled = !"disabled".equals(mode);
    if (!Set.of("mock", "disabled", "live").contains(mode)) {
      throw new IllegalStateException("Unsupported app.sms.mode");
    }
    if (mock) {
      String[] profiles = environment.getActiveProfiles();
      if (profiles.length == 0) profiles = environment.getDefaultProfiles();
      if (
        profiles.length == 0 ||
        Arrays.stream(profiles).anyMatch(
          profile -> !Set.of("local", "test").contains(profile)
        )
      ) {
        throw new IllegalStateException(
          "Mock SMS is restricted to local/test profiles"
        );
      }
      if (
        !Set.of("127.0.0.1", "::1").contains(
          environment.getProperty("server.address", "")
        )
      ) {
        throw new IllegalStateException(
          "Mock SMS requires a loopback server.address"
        );
      }
    }
    sender = senders.get(mode + "SmsSender");
    if (sender == null) throw new IllegalStateException(
      "Configure a liveSmsSender adapter before enabling live SMS"
    );
  }

  public boolean isMock() {
    return mock;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void send(String phone, String code) {
    sender.send(phone, code);
  }
}
