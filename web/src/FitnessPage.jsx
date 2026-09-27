import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  AlertCircle,
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  FileText,
  HeartPulse,
  RotateCcw,
  UploadCloud,
} from "lucide-react";
import { api, json } from "./api";

const STAGES = {
  CHILD: "유소년기 · 만 11~12세",
  TEEN: "청소년기 · 만 13~18세",
  ADULT: "성인기 · 만 19~64세",
  SENIOR: "어르신기 · 만 65세 이상",
};
const LABELS = {
  shuttle15: "15m 왕복오래달리기 (회)",
  shuttle20: "20m 왕복오래달리기 (회)",
  vo2: "트레드밀/스텝검사 (ml/kg/min)",
  grip: "상대악력 (%)",
  curlUp: "윗몸말아올리기 (회)",
  repeatJump: "반복점프 (회)",
  sitUp: "교차윗몸일으키기 (회)",
  flexibility: "앉아윗몸앞으로굽히기 (cm)",
  sideHop: "반복옆뛰기 (회)",
  longJump: "제자리멀리뛰기 (cm)",
  handEyeCount: "눈-손 협응력 (회)",
  handEyeTime: "눈-손 협응력 (초)",
  illinois: "일리노이 민첩성 (초)",
  airTime: "체공시간 (초)",
  shuttle10: "10m 왕복달리기 (초)",
  reaction: "반응시간 (초)",
  walk2min: "2분 제자리걷기 (회)",
  walk6min: "6분 걷기 (m)",
  chairStand: "30초 의자앉았다 일어서기 (회)",
  upGo3m: "3m 검사 (초)",
  figure8: "8자보행 (초)",
  bmi: "BMI (kg/㎡)",
  bodyFat: "체지방률 (%)",
  waistHeightRatio: "허리둘레-신장비",
};
const GROUPS = {
  CHILD: [
    "shuttle15",
    "grip",
    "curlUp",
    "flexibility",
    "sideHop",
    "longJump",
    "handEyeCount",
    "bmi",
    "waistHeightRatio",
  ],
  TEEN: [
    "shuttle20",
    "vo2",
    "grip",
    "curlUp",
    "repeatJump",
    "flexibility",
    "illinois",
    "airTime",
    "handEyeTime",
    "bmi",
    "bodyFat",
  ],
  ADULT: [
    "shuttle20",
    "vo2",
    "grip",
    "sitUp",
    "flexibility",
    "shuttle10",
    "reaction",
    "longJump",
    "airTime",
    "bmi",
    "bodyFat",
  ],
  SENIOR: [
    "walk2min",
    "walk6min",
    "grip",
    "chairStand",
    "flexibility",
    "upGo3m",
    "figure8",
  ],
};
const stageFor = (age) =>
  age >= 65
    ? "SENIOR"
    : age >= 19
      ? "ADULT"
      : age >= 13
        ? "TEEN"
        : age >= 11
          ? "CHILD"
          : null;
const ageAtDate = (birth, measured) => {
  if (!birth || !measured) return null;
  const [by, bm, bd] = birth.split("-").map(Number);
  const [my, mm, md] = measured.split("-").map(Number);
  return my - by - (mm < bm || (mm === bm && md < bd) ? 1 : 0);
};

// 국민체력100 결과지 업로드와 참고 등급 분석 결과를 보여준다.
export default function FitnessPage({ user, openAuth, notify }) {
  const [file, setFile] = useState(null);
  const [draft, setDraft] = useState(null);
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  const [history, setHistory] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const stage = stageFor(user?.age ?? -1);
  const relevantStage =
    preview?.stage ||
    stageFor(ageAtDate(user?.birthDate, draft?.measuredOn)) ||
    stage;
  const relevant = useMemo(() => GROUPS[relevantStage] || [], [relevantStage]);

  async function loadHistory() {
    if (!user) return;
    setLoadingHistory(true);
    try {
      setHistory(await api("/api/fitness/assessments"));
    } catch (caught) {
      setError(caught.message);
    } finally {
      setLoadingHistory(false);
    }
  }
  useEffect(() => {
    loadHistory();
  }, [user?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  async function scan(event) {
    event.preventDefault();
    if (!file) return;
    setBusy("scan");
    setError("");
    setPreview(null);
    try {
      const body = new FormData();
      body.append("file", file);
      const response = await api("/api/fitness/parse", {
        method: "POST",
        body,
      });
      setDraft({
        measuredOn: response.parsed.measuredOn || "",
        reportedOfficialGrade: response.parsed.reportedOfficialGrade ?? "",
        values: response.parsed.values || {},
        warnings: response.parsed.warnings || [],
        usedOcr: response.parsed.usedOcr,
      });
      setPreview(response.preview);
      notify("결과지를 읽었습니다. 추출값을 원본과 대조해 주세요.");
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy("");
    }
  }
  const payload = () => ({
    measuredOn: draft.measuredOn,
    reportedOfficialGrade:
      draft.reportedOfficialGrade === ""
        ? null
        : Number(draft.reportedOfficialGrade),
    values: Object.fromEntries(
      Object.entries(draft.values)
        .filter(([, value]) => value !== "" && value != null)
        .map(([key, value]) => [key, Number(value)]),
    ),
  });
  async function recalculate() {
    setBusy("preview");
    setError("");
    try {
      setPreview(
        await api("/api/fitness/preview", {
          method: "POST",
          body: json(payload()),
        }),
      );
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy("");
    }
  }
  async function save() {
    setBusy("save");
    setError("");
    try {
      await api("/api/fitness/assessments", {
        method: "POST",
        body: json(payload()),
      });
      notify("체력 평가를 저장했습니다.");
      setDraft(null);
      setPreview(null);
      setFile(null);
      await loadHistory();
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy("");
    }
  }
  const updateValue = (key, value) => {
    setDraft((previous) => ({
      ...previous,
      values: { ...previous.values, [key]: value },
    }));
    setPreview(null);
  };

  return (
    <>
      <div className="page-intro fitness-intro">
        <div>
          <span className="eyebrow">MY FITNESS 100</span>
          <h1>
            나의 체력을 알고,
            <br />
            <em>더 잘 움직여요.</em>
          </h1>
          <p>
            국민체력100 결과지의 측정값을 확인하고
            <br />
            나이대에 맞는 참고 등급을 살펴보세요.
          </p>
        </div>
        <div className="fitness-intro-art">
          <Activity size={110} strokeWidth={1.2} />
        </div>
      </div>
      <section className="page-section">
        <div className="fitness-reservation-guide">
          <div>
            <span className="eyebrow">NO RESULT PDF YET?</span>
            <h2>국민체력100 결과지가 아직 없나요?</h2>
            <p>
              공식 홈페이지에서 체력측정을 예약한 뒤, 측정 결과지를 받으면
              이곳에 PDF를 업로드해 주세요.
            </p>
          </div>
          <div className="fitness-reservation-actions">
            <a
              className="button button-dark"
              href="https://nfa.kspo.or.kr/reserve/selectReserveStep1.kspo"
              target="_blank"
              rel="noopener noreferrer"
            >
              국민체력100 측정 예약하기 <ArrowRight size={16} />
            </a>
            <a href="/fitness-centers">가까운 체력측정센터 찾기</a>
          </div>
        </div>
      </section>
      {!user ? (
        <section className="page-section">
          <div className="fitness-guest">
            <HeartPulse size={30} />
            <h2>로그인이 필요해요</h2>
            <p>체력 결과는 회원 본인에게만 표시됩니다.</p>
            <button className="button button-dark" onClick={openAuth}>
              로그인하기 <ArrowRight size={16} />
            </button>
          </div>
        </section>
      ) : (
        <>
          <section className="page-section">
            <div className="section-heading">
              <div>
                <span className="eyebrow">PERSONAL STANDARD</span>
                <h2>내 평가 기준</h2>
                <p>
                  생년월일로 계산한 만 나이를 사용합니다. 과거 결과지는 검사일
                  당시 나이로 평가합니다.
                </p>
              </div>
            </div>
            <div className="fitness-facts">
              <div>
                <span>현재 만 나이</span>
                <strong>{user.age ?? "—"}세</strong>
              </div>
              <div>
                <span>연령 구분</span>
                <strong>
                  {stage ? STAGES[stage] : "평가 대상 연령 확인 필요"}
                </strong>
              </div>
              <div>
                <span>평가 방식</span>
                <strong>성별·연령별 공식 경계값</strong>
              </div>
            </div>
          </section>
          <section className="page-section">
            <div className="section-heading">
              <div>
                <span className="eyebrow">UPLOAD RESULT</span>
                <h2>결과지 업로드</h2>
                <p>
                  PDF 최대 10MB · 5쪽까지. 서버는 원본 PDF를 저장하지 않습니다.
                </p>
              </div>
            </div>
            <div className="fitness-upload-panel">
              <form onSubmit={scan}>
                <label className="fitness-drop">
                  <UploadCloud size={29} />
                  <strong>
                    {file ? file.name : "국민체력100 결과지 PDF 선택"}
                  </strong>
                  <span>
                    텍스트 PDF 또는 스캔본 PDF · 스캔본은 서버 OCR 설치 필요
                  </span>
                  <input
                    type="file"
                    accept=".pdf,application/pdf"
                    onChange={(event) => {
                      setFile(event.target.files?.[0] || null);
                      setDraft(null);
                      setPreview(null);
                      setError("");
                    }}
                  />
                </label>
                <button
                  className="button button-dark"
                  disabled={!file || !!busy}
                >
                  {busy === "scan" ? "PDF 분석 중..." : "결과지 분석하기"}{" "}
                  <ArrowRight size={16} />
                </button>
              </form>
              <p className="fitness-note">
                <AlertCircle size={15} /> 공식 인증서가 아닌 SportMap의 참고용
                A~D 등급입니다. 원본 결과지와 추출값을 대조해 주세요.
              </p>
            </div>
          </section>
          {draft && (
            <section className="page-section">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">REVIEW VALUES</span>
                  <h2>추출값 확인</h2>
                  <p>
                    OCR 결과가 틀릴 수 있습니다. 원본 PDF와 비교한 뒤 수정해
                    주세요.
                  </p>
                </div>
              </div>
              <div className="fitness-review panel">
                <div className="fitness-form-top">
                  <label>
                    검사일
                    <input
                      type="date"
                      value={draft.measuredOn}
                      onChange={(event) => {
                        setDraft({ ...draft, measuredOn: event.target.value });
                        setPreview(null);
                      }}
                    />
                  </label>
                  <label>
                    결과지 인증등급
                    <select
                      value={draft.reportedOfficialGrade}
                      onChange={(event) => {
                        setDraft({
                          ...draft,
                          reportedOfficialGrade: event.target.value,
                        });
                        setPreview(null);
                      }}
                    >
                      <option value="">기재 없음 / 확인 불가</option>
                      {stage === "CHILD" && <option value="0">참가증</option>}
                      {[1, 2, 3, 4, 5, 6].map((n) => (
                        <option value={n} key={n}>
                          {n}등급
                        </option>
                      ))}
                    </select>
                  </label>
                </div>
                <div className="fitness-metrics">
                  {relevant.map((key) => (
                    <label key={key}>
                      <span>{LABELS[key]}</span>
                      <input
                        type="number"
                        step="any"
                        value={draft.values[key] ?? ""}
                        onChange={(event) =>
                          updateValue(key, event.target.value)
                        }
                        placeholder="미측정"
                      />
                    </label>
                  ))}
                </div>
                {draft.warnings.map((warning, index) => (
                  <p className="fitness-warning" key={index}>
                    <AlertCircle size={14} /> {warning}
                  </p>
                ))}
                <button
                  className="button button-dark"
                  disabled={!draft.measuredOn || !!busy}
                  onClick={recalculate}
                >
                  <RotateCcw size={16} />{" "}
                  {busy === "preview" ? "계산 중..." : "수치로 다시 평가하기"}
                </button>
              </div>
            </section>
          )}
          {preview && (
            <section className="page-section">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">FITNESS ASSESSMENT</span>
                  <h2>평가 결과</h2>
                </div>
              </div>
              <div className="fitness-result panel">
                <div
                  className={`fitness-grade ${preview.grade ? "grade-" + preview.grade.toLowerCase() : ""}`}
                >
                  {preview.grade || "?"}
                </div>
                <div>
                  <span className="eyebrow">
                    {STAGES[preview.stage]} · 검사 당시 만 {preview.ageAtTest}세
                  </span>
                  <h3>
                    {preview.status === "READY"
                      ? `${preview.grade}등급 · 참고 점수 ${preview.score}점`
                      : "추가 확인이 필요합니다"}
                  </h3>
                  <p>{preview.basis}</p>
                  {preview.missing?.length > 0 && (
                    <p className="fitness-warning">
                      <AlertCircle size={14} /> {preview.missing.join(", ")}
                    </p>
                  )}
                  <small>
                    공식 1~3등급 → A~C, 공식 4~6등급 → D. 점수는 항목별 경계값
                    통과 수준의 평균이며 공식 점수가 아닙니다.
                  </small>
                </div>
              </div>
              {preview.status === "READY" && (
                <button
                  className="button button-dark fitness-save"
                  onClick={save}
                  disabled={!!busy}
                >
                  {busy === "save" ? "저장 중..." : "확인한 결과 저장하기"}{" "}
                  <CheckCircle2 size={17} />
                </button>
              )}
            </section>
          )}
          {error && (
            <div className="fitness-error" role="alert">
              <AlertCircle size={17} /> {error}
            </div>
          )}
          <section className="page-section">
            <div className="section-heading">
              <div>
                <span className="eyebrow">MY HISTORY</span>
                <h2>이전 평가</h2>
                <p>검사일 기준으로 정리한 내 체력 변화입니다.</p>
              </div>
            </div>
            {loadingHistory ? (
              <p className="muted">평가 내역을 불러오는 중...</p>
            ) : history.length ? (
              <div className="fitness-history">
                {history.map((item) => (
                  <article key={item.id}>
                    <span
                      className={`fitness-history-grade grade-${item.grade.toLowerCase()}`}
                    >
                      {item.grade}
                    </span>
                    <div>
                      <strong>{STAGES[item.stage]}</strong>
                      <small>
                        <CalendarDays size={13} /> {item.measuredOn} · 검사 당시
                        만 {item.ageAtTest}세
                      </small>
                    </div>
                    <b>{item.score}점</b>
                  </article>
                ))}
              </div>
            ) : (
              <div className="fitness-empty">
                <FileText size={23} /> 아직 저장된 평가가 없습니다.
              </div>
            )}
          </section>
        </>
      )}
    </>
  );
}
