import { useEffect, useState } from "react";
import { api, json } from "./api";

export default function PhoneVerificationField({
  value,
  onChange,
  onVerified,
}) {
  const [challenge, setChallenge] = useState(null);
  const [proof, setProof] = useState(null);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [now, setNow] = useState(Date.now());
  const [retryAt, setRetryAt] = useState(0);
  const expiresAt = proof?.expiresAt || challenge?.expiresAt;
  const remaining = expiresAt
    ? Math.max(0, Math.ceil((Date.parse(expiresAt) - now) / 1000))
    : 0;
  const retrySeconds = Math.max(0, Math.ceil((retryAt - now) / 1000));

  useEffect(() => {
    if (!challenge) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [challenge]);

  useEffect(() => {
    if (proof && remaining === 0) {
      onVerified(null);
      setMessage("가입 가능 시간이 만료됐습니다. 휴대폰을 다시 인증해 주세요.");
    }
  }, [proof, remaining, onVerified]);

  function changePhone(next) {
    onChange(next);
    onVerified(null);
    setChallenge(null);
    setProof(null);
    setCode("");
    setMessage("");
    setError("");
    setRetryAt(0);
    setNow(Date.now());
  }

  async function send() {
    setBusy(true);
    setError("");
    onVerified(null);
    setProof(null);
    setChallenge(null);
    setCode("");
    setMessage("");
    try {
      const result = await api("/api/users/phone/send", {
        method: "POST",
        body: json({ phoneNumber: value }),
      });
      setNow(Date.now());
      setRetryAt(Date.now() + result.retryAfterSeconds * 1000);
      setChallenge(result);
      setMessage(
        result.mock
          ? "개발·시연 모드입니다. 실제 문자는 발송되지 않습니다."
          : "인증번호를 발송했습니다. 수신한 번호를 입력해 주세요.",
      );
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy(false);
    }
  }

  async function verify() {
    setBusy(true);
    setError("");
    try {
      const result = await api("/api/users/phone/verify", {
        method: "POST",
        body: json({
          phoneNumber: value,
          requestToken: challenge.requestToken,
          code,
        }),
      });
      setNow(Date.now());
      setProof(result);
      setCode("");
      // Remove the development code as soon as verification succeeds.
      setChallenge((previous) => ({ ...previous, developmentCode: undefined }));
      onVerified({
        requestToken: challenge.requestToken,
        verificationToken: result.verificationToken,
      });
      setMessage(
        "휴대폰 인증이 완료됐습니다. 10분 안에 회원가입을 완료해 주세요.",
      );
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="phone-verification">
      <label>
        휴대폰 번호
        <input
          name="phoneNumber"
          type="tel"
          autoComplete="tel-national"
          value={value}
          onChange={(event) => changePhone(event.target.value)}
          disabled={busy}
          required
          maxLength={15}
          placeholder="010-1234-5678"
        />
      </label>
      <button
        className="email-action"
        type="button"
        onClick={send}
        disabled={
          busy ||
          retrySeconds > 0 ||
          !/^010[0-9]{8}$/.test(value.replace(/[ -]/g, ""))
        }
      >
        {busy
          ? "처리 중..."
          : retrySeconds > 0
            ? `${retrySeconds}초 후 재발송 가능`
            : challenge
              ? "인증번호 다시 받기"
              : "휴대폰 인증번호 받기"}
      </button>
      <div aria-live="polite">
        {message && <small className="email-status">{message}</small>}
        {challenge?.mock && challenge.developmentCode && (
          <p className="demo-hint">
            테스트용 인증번호: <strong>{challenge.developmentCode}</strong>
          </p>
        )}
        {challenge && !proof && (
          <>
            <label>
              인증번호
              <input
                value={code}
                onChange={(event) =>
                  setCode(event.target.value.replace(/[^0-9]/g, "").slice(0, 6))
                }
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                disabled={busy || remaining === 0}
                placeholder="6자리 숫자"
              />
            </label>
            <small className="input-hint">
              {remaining > 0
                ? `남은 시간 ${Math.floor(remaining / 60)}분 ${remaining % 60}초`
                : "인증번호가 만료됐습니다. 다시 발송해 주세요."}
            </small>
            <button
              className="email-action"
              type="button"
              onClick={verify}
              disabled={busy || code.length !== 6 || remaining === 0}
            >
              인증번호 확인
            </button>
          </>
        )}
        {error && (
          <div className="form-error" role="alert">
            {error}
          </div>
        )}
      </div>
    </div>
  );
}
