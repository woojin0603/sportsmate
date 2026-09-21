package kr.or.sportmap.member.sms;

/** A provider adapter must throw on rejected/failed delivery and never log the code. */
public interface SmsSender {
  void send(String phoneNumber, String code);
}
