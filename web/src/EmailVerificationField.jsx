import { useEffect, useState } from "react";
import { api, json } from "./api";

export default function EmailVerificationField({
  value,
  onChange,
  onVerified,
}) {
  const [challenge, setChallenge] = useState(null);
  const [verified, setVerified] = useState(false);
  const [busy, setBusy] = useState(false);
  const [stopped, setStopped] = useState(false);
  const [error, setError] = useState("");
  const [now, setNow] = useState(Date.now());
  const [retryAt, setRetryAt] = useState(0);
  const remaining = challenge
    ? Math.max(0, Math.ceil((Date.parse(challenge.expiresAt) - now) / 1000))
    : 0;
  const retrySeconds = Math.max(0, Math.ceil((retryAt - now) / 1000));
  const expired = Boolean(challenge) && remaining === 0;

  useEffect(() => {
    if (!challenge) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [challenge]);

  useEffect(() => {
    if (expired) onVerified("");
  }, [expired, onVerified]);

  // Wait for each request to complete before scheduling another check.
  useEffect(() => {
    if (!challenge || verified || expired || stopped) return;
    let active = true;
    let timer;
    async function check() {
      try {
        const result = await api("/api/users/email/status", {
          method: "POST",
          body: json({ email: value, requestToken: challenge.requestToken }),
        });
        if (!active) return;
        if (result.verified) {
          setVerified(true);
          onVerified(result.verificationToken);
          return;
        }
      } catch (caught) {
        if (!active) return;
        setError(caught.message);
        setStopped(true);
        onVerified("");
        return;
      }
      if (active) timer = window.setTimeout(check, 2500);
    }
    check();
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [challenge, verified, expired, stopped, value, onVerified]);

  function changeEmail(next) {
    onChange(next);
    onVerified("");
    setChallenge(null);
    setVerified(false);
    setStopped(false);
    setError("");
    setRetryAt(0);
    setNow(Date.now());
  }

  async function send() {
    setBusy(true);
    setError("");
    setChallenge(null);
    setVerified(false);
    setStopped(false);
    onVerified("");
    try {
      const result = await api("/api/users/email/send", {
        method: "POST",
        body: json({ email: value }),
      });
      setNow(Date.now());
      setRetryAt(Date.now() + result.retryAfterSeconds * 1000);
      setChallenge(result);
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="email-verification">
      <label>
        이메일
        <input
          name="email"
          type="email"
          autoComplete="email"
          value={value}
          onChange={(event) => changeEmail(event.target.value)}
          required
          maxLength={255}
          disabled={busy}
          placeholder="name@example.com"
        />
      </label>
      <button
        className="email-action"
        type="button"
        onClick={send}
        disabled={
          busy || retrySeconds > 0 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
        }
      >
        {busy
          ? "처리 중..."
          : retrySeconds > 0
            ? `${retrySeconds}초 후 재발송 가능`
            : challenge
              ? "인증 메일 다시 보내기"
              : "이메일 인증하기"}
      </button>
      {challenge && (
        <div className="email-verification-status">
          <small className="email-status" role="status">
            {expired
              ? "인증 시간이 만료됐습니다. 이메일을 다시 인증해 주세요."
              : verified
                ? "이메일 인증이 완료됐습니다."
                : challenge.mock
                  ? "개발·시연 모드입니다. 실제 이메일은 발송되지 않습니다."
                  : "인증 메일을 보냈습니다. 메일 안의 인증 버튼을 눌러 주세요."}
          </small>
          {challenge.mock &&
            !verified &&
            !expired &&
            challenge.developmentConfirmationUrl && (
              <a
                className="email-action"
                href={challenge.developmentConfirmationUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                테스트용 이메일 인증 링크 열기
              </a>
            )}
          {!expired && (
            <small className="input-hint">
              가입 완료까지 남은 시간 {Math.floor(remaining / 60)}분{" "}
              {remaining % 60}초
            </small>
          )}
          {stopped && !expired && (
            <button
              className="email-action"
              type="button"
              onClick={() => {
                setError("");
                setStopped(false);
              }}
            >
              인증 상태 다시 확인
            </button>
          )}
        </div>
      )}
      {error && (
        <div className="form-error" role="alert">
          {error}
        </div>
      )}
    </div>
  );
}
