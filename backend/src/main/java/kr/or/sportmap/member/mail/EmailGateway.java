package kr.or.sportmap.member.mail;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EmailGateway {

  private static final String CONFIRM_PATH = "/api/users/email/confirm";
  private final VerificationEmailSender sender;
  private final boolean mock;
  private final String publicBaseUrl;

  public EmailGateway(
    Map<String, VerificationEmailSender> senders,
    @Value("${app.mail.mode:disabled}") String mode,
    @Value("${app.mail.public-base-url:}") String publicBaseUrl,
    Environment environment
  ) {
    if (
      !Set.of("mock", "disabled", "live").contains(mode)
    ) throw new IllegalStateException("Unsupported app.mail.mode");
    mock = "mock".equals(mode);
    if (mock) {
      String[] profiles = environment.getActiveProfiles();
      if (profiles.length == 0) profiles = environment.getDefaultProfiles();
      if (
        profiles.length == 0 ||
        Arrays.stream(profiles).anyMatch(
          p -> !Set.of("local", "test").contains(p)
        )
      ) {
        throw new IllegalStateException(
          "Mock email is restricted to local/test profiles"
        );
      }
      if (
        !Set.of("127.0.0.1", "::1").contains(
          environment.getProperty("server.address", "")
        )
      ) {
        throw new IllegalStateException(
          "Mock email requires a loopback server.address"
        );
      }
    }
    this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    if ("live".equals(mode)) {
      URI base = URI.create(this.publicBaseUrl);
      if (
        !"https".equals(base.getScheme()) ||
        base.getHost() == null ||
        base.getUserInfo() != null ||
        base.getQuery() != null ||
        base.getFragment() != null ||
        !base.getPath().isEmpty()
      ) {
        throw new IllegalStateException(
          "Live email requires MAIL_PUBLIC_BASE_URL=https://your-public-host (no path/query)"
        );
      }
    }
    sender = senders.get(mode + "VerificationEmailSender");
    if (sender == null) throw new IllegalStateException(
      "No email sender configured for selected mode"
    );
  }

  public boolean isMock() {
    return mock;
  }

  public String confirmationUrl(String token) {
    return (mock ? "" : publicBaseUrl) + CONFIRM_PATH + "?token=" + token;
  }

  public void send(String email, String url) {
    sender.send(email, url);
  }
}
