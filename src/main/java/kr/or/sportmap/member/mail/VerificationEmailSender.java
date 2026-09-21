package kr.or.sportmap.member.mail;

/** Deliver the confirmation URL, or throw on failure. Never log the URL/token. */
public interface VerificationEmailSender {
  void send(String email, String confirmationUrl);
}
