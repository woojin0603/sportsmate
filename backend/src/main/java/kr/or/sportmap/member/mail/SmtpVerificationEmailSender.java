package kr.or.sportmap.member.mail;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@Component("liveVerificationEmailSender")
public class SmtpVerificationEmailSender implements VerificationEmailSender {

  private final ObjectProvider<JavaMailSender> senders;
  private final String host;
  private final String from;

  public SmtpVerificationEmailSender(
    ObjectProvider<JavaMailSender> senders,
    @Value("${spring.mail.host:}") String host,
    @Value("${app.mail.from:}") String from
  ) {
    this.senders = senders;
    this.host = host;
    this.from = from;
  }

  public void send(String email, String confirmationUrl) {
    JavaMailSender sender = senders.getIfAvailable();
    if (host.isBlank() || from.isBlank() || sender == null) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "이메일 발송 설정이 필요합니다. 관리자에게 문의해 주세요"
      );
    }
    try {
      var message = sender.createMimeMessage();
      var helper = new MimeMessageHelper(
        message,
        true,
        StandardCharsets.UTF_8.name()
      );
      helper.setFrom(from);
      helper.setTo(email);
      helper.setSubject("[SportMap] 회원가입 이메일 인증");
      helper.setText(
        "SportMap 이메일 인증\n아래 링크를 10분 안에 열어 인증을 완료해 주세요.\n" +
          confirmationUrl +
          "\n요청하지 않았다면 무시하세요.",
        "<div style=\"font-family:sans-serif;padding:32px;color:#173e32\"><h2>SportMap 이메일 인증</h2><p>아래 버튼을 눌러 인증을 완료해 주세요.</p><a href=\"" +
          HtmlUtils.htmlEscape(confirmationUrl) +
          "\">이메일 인증 완료</a><p>발송 후 10분 동안 유효합니다. 요청하지 않았다면 무시하세요.</p></div>"
      );
      sender.send(message);
    } catch (MessagingException | MailException error) {
      throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "인증 메일을 보내지 못했습니다. 발송 설정을 확인해 주세요"
      );
    }
  }
}
