import { useEffect, useState } from "react";
import {
  Bell,
  MessageCircle,
  MonitorCog,
  Settings,
  UsersRound,
} from "lucide-react";
import { api, json } from "./api";
import { formatPhone } from "./phone";

const categories = [
  ["site", "사이트 설정", Settings],
  ["popup", "팝업 설정", Bell],
  ["users", "회원 권한 설정", UsersRound],
  ["notices", "공지사항 관리", MonitorCog],
  ["qna", "Q&A", MessageCircle],
];
const defaults = {
  siteTitle: "SportMap",
  announcement: "",
  popupEnabled: false,
  popupTitle: "",
  popupContent: "",
};

// 운영 통계와 각 관리 기능을 카테고리별로 제공한다.
export default function AdminPage({ notify, onSettingsSaved }) {
  const [category, setCategory] = useState("site");
  const [overview, setOverview] = useState(null);
  const [overviewError, setOverviewError] = useState("");
  const [settings, setSettings] = useState(defaults);
  const [users, setUsers] = useState(null);
  const [notices, setNotices] = useState(null);
  const [questions, setQuestions] = useState(null);
  const [selectedQuestion, setSelectedQuestion] = useState(null);
  const [comments, setComments] = useState([]);
  const [answer, setAnswer] = useState("");
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [noticeForm, setNoticeForm] = useState({
    id: null,
    title: "",
    content: "",
  });

  // 다른 관리 API 오류와 분리해 시설·프로그램 통계를 항상 조회한다.
  useEffect(() => {
    api("/api/admin/overview")
      .then((data) => {
        setOverview(data);
        setOverviewError("");
      })
      .catch((failure) => setOverviewError(failure.message));
  }, [refresh]);
  useEffect(() => {
    api("/api/site-settings")
      .then((data) => setSettings({ ...defaults, ...data }))
      .catch((failure) => setError(failure.message));
  }, [refresh]);

  // 선택한 카테고리에 필요한 데이터만 가져온다.
  useEffect(() => {
    setError("");
    if (category === "users")
      api(`/api/admin/users?page=${page}&size=15`)
        .then(setUsers)
        .catch((failure) => setError(failure.message));
    if (category === "notices")
      api("/api/notices?page=0&size=50")
        .then(setNotices)
        .catch((failure) => setError(failure.message));
    if (category === "qna")
      api("/api/qna?page=0&size=100")
        .then(setQuestions)
        .catch((failure) => setError(failure.message));
  }, [category, page, refresh]);

  // 질문 선택 시 기존 관리자 답변을 조회한다.
  useEffect(() => {
    if (!selectedQuestion) {
      setComments([]);
      return;
    }
    api(`/api/qna/${selectedQuestion.id}/comments`)
      .then(setComments)
      .catch((failure) => setError(failure.message));
  }, [selectedQuestion, refresh]);

  async function saveSettings(event, message) {
    event.preventDefault();
    setBusy(true);
    try {
      const saved = await api("/api/admin/site-settings", {
        method: "PUT",
        body: json(settings),
      });
      setSettings(saved);
      onSettingsSaved?.(saved);
      notify(message);
    } catch (failure) {
      notify(failure.message, "error");
    } finally {
      setBusy(false);
    }
  }
  async function changeRole(member, role) {
    try {
      await api(`/api/admin/users/${member.id}/role`, {
        method: "PATCH",
        body: json({ role }),
      });
      notify(`${member.username} 권한을 변경했습니다.`);
      setRefresh((value) => value + 1);
    } catch (failure) {
      notify(failure.message, "error");
    }
  }
  async function saveNotice(event) {
    event.preventDefault();
    setBusy(true);
    try {
      await api(
        noticeForm.id
          ? `/api/admin/notices/${noticeForm.id}`
          : "/api/admin/notices",
        {
          method: noticeForm.id ? "PUT" : "POST",
          body: json({ title: noticeForm.title, content: noticeForm.content }),
        },
      );
      notify(noticeForm.id ? "공지를 수정했습니다." : "공지를 등록했습니다.");
      setNoticeForm({ id: null, title: "", content: "" });
      setRefresh((value) => value + 1);
    } catch (failure) {
      notify(failure.message, "error");
    } finally {
      setBusy(false);
    }
  }
  async function deleteNotice(post) {
    if (!window.confirm(`'${post.title}' 공지를 삭제할까요?`)) return;
    try {
      await api(`/api/admin/notices/${post.id}`, { method: "DELETE" });
      notify("공지를 삭제했습니다.");
      setRefresh((value) => value + 1);
    } catch (failure) {
      notify(failure.message, "error");
    }
  }
  async function submitAnswer(event) {
    event.preventDefault();
    setBusy(true);
    try {
      await api(`/api/qna/${selectedQuestion.id}/comments`, {
        method: "POST",
        body: json({ content: answer }),
      });
      setAnswer("");
      notify("관리자 답변을 등록했습니다.");
      setRefresh((value) => value + 1);
    } catch (failure) {
      notify(failure.message, "error");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="admin-console">
      <div className="admin-intro">
        <div>
          <span className="eyebrow">ADMIN DASHBOARD</span>
          <h1>서비스 관리</h1>
          <p>원하는 카테고리를 선택해 사이트 운영 항목을 관리하세요.</p>
        </div>
      </div>
      <div className="admin-stats">
        {[
          ["회원", overview?.users],
          ["체육시설", overview?.facilities],
          ["프로그램", overview?.programs],
          ["예약·신청", overview?.reservations],
          ["공지", overview?.notices],
        ].map(([label, value]) => (
          <div className="admin-stat" key={label}>
            <span>{label}</span>
            <strong>{value?.toLocaleString("ko-KR") ?? "—"}</strong>
          </div>
        ))}
      </div>
      {overviewError && (
        <div className="form-error">
          운영 통계를 불러오지 못했습니다: {overviewError}{" "}
          <button onClick={() => setRefresh((v) => v + 1)}>다시 조회</button>
        </div>
      )}
      <div className="admin-workspace">
        <nav className="admin-categories" aria-label="관리 카테고리">
          {categories.map(([id, label, Icon]) => (
            <button
              key={id}
              className={category === id ? "active" : ""}
              onClick={() => setCategory(id)}
            >
              <Icon size={19} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
        <section className="admin-category-panel">
          {error && <div className="form-error">{error}</div>}
          {category === "site" && (
            <SiteSettings
              settings={settings}
              setSettings={setSettings}
              busy={busy}
              save={saveSettings}
            />
          )}
          {category === "popup" && (
            <PopupSettings
              settings={settings}
              setSettings={setSettings}
              busy={busy}
              save={saveSettings}
            />
          )}
          {category === "users" && (
            <UserSettings
              users={users}
              page={page}
              setPage={setPage}
              changeRole={changeRole}
            />
          )}
          {category === "notices" && (
            <NoticeSettings
              notices={notices}
              form={noticeForm}
              setForm={setNoticeForm}
              busy={busy}
              save={saveNotice}
              remove={deleteNotice}
            />
          )}
          {category === "qna" && (
            <QnaSettings
              questions={questions}
              selected={selectedQuestion}
              setSelected={setSelectedQuestion}
              comments={comments}
              answer={answer}
              setAnswer={setAnswer}
              busy={busy}
              submit={submitAnswer}
            />
          )}
        </section>
      </div>
    </div>
  );
}

function SiteSettings({ settings, setSettings, busy, save }) {
  return (
    <>
      <h2>사이트 설정</h2>
      <p className="admin-section-help">
        사이트 제목과 모든 화면 상단에 표시할 안내 문구를 관리합니다.
      </p>
      <form
        className="admin-notice-form"
        onSubmit={(event) => save(event, "사이트 설정을 저장했습니다.")}
      >
        <label>
          사이트 제목
          <input
            value={settings.siteTitle}
            maxLength={80}
            required
            onChange={(e) =>
              setSettings({ ...settings, siteTitle: e.target.value })
            }
          />
        </label>
        <label>
          전체 화면 안내 문구
          <textarea
            value={settings.announcement}
            maxLength={500}
            rows={4}
            placeholder="비워두면 표시하지 않습니다."
            onChange={(e) =>
              setSettings({ ...settings, announcement: e.target.value })
            }
          />
        </label>
        <button className="button button-dark" disabled={busy}>
          사이트 설정 저장
        </button>
      </form>
    </>
  );
}

function PopupSettings({ settings, setSettings, busy, save }) {
  return (
    <>
      <h2>팝업 설정</h2>
      <p className="admin-section-help">
        활성화하면 일반 사용자가 사이트에 접속할 때 팝업을 표시합니다.
      </p>
      <form
        className="admin-notice-form"
        onSubmit={(event) => save(event, "팝업 설정을 저장했습니다.")}
      >
        <label className="admin-toggle">
          <input
            type="checkbox"
            checked={settings.popupEnabled}
            onChange={(e) =>
              setSettings({ ...settings, popupEnabled: e.target.checked })
            }
          />
          <span>팝업 사용</span>
        </label>
        <label>
          팝업 제목
          <input
            value={settings.popupTitle}
            maxLength={120}
            required={settings.popupEnabled}
            onChange={(e) =>
              setSettings({ ...settings, popupTitle: e.target.value })
            }
          />
        </label>
        <label>
          팝업 내용
          <textarea
            value={settings.popupContent}
            maxLength={1000}
            rows={7}
            required={settings.popupEnabled}
            onChange={(e) =>
              setSettings({ ...settings, popupContent: e.target.value })
            }
          />
        </label>
        <div className="admin-popup-preview">
          <small>미리보기</small>
          <strong>{settings.popupTitle || "팝업 제목"}</strong>
          <p>{settings.popupContent || "팝업 내용이 여기에 표시됩니다."}</p>
        </div>
        <button className="button button-dark" disabled={busy}>
          팝업 설정 저장
        </button>
      </form>
    </>
  );
}

function UserSettings({ users, page, setPage, changeRole }) {
  return (
    <>
      <h2>회원 권한 설정</h2>
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th>아이디</th>
              <th>성명</th>
              <th>이메일</th>
              <th>전화번호</th>
              <th>권한</th>
            </tr>
          </thead>
          <tbody>
            {users?.content?.map((member) => (
              <tr key={member.id}>
                <td>{member.username}</td>
                <td>{member.fullName}</td>
                <td>{member.email}</td>
                <td>{formatPhone(member.phoneNumber)}</td>
                <td>
                  <select
                    value={member.role}
                    disabled={member.username === "admin"}
                    onChange={(e) => changeRole(member, e.target.value)}
                  >
                    <option value="USER">일반 사용자</option>
                    <option value="ADMIN">관리자</option>
                  </select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="admin-pager">
        <button disabled={page === 0} onClick={() => setPage(page - 1)}>
          이전
        </button>
        <span>
          {page + 1} / {Math.max(1, users?.totalPages || 1)}
        </span>
        <button
          disabled={page + 1 >= (users?.totalPages || 1)}
          onClick={() => setPage(page + 1)}
        >
          다음
        </button>
      </div>
    </>
  );
}

function NoticeSettings({ notices, form, setForm, busy, save, remove }) {
  return (
    <>
      <h2>공지사항 관리</h2>
      <form className="admin-notice-form" onSubmit={save}>
        <input
          value={form.title}
          maxLength={200}
          required
          placeholder="공지 제목"
          onChange={(e) => setForm({ ...form, title: e.target.value })}
        />
        <textarea
          value={form.content}
          maxLength={5000}
          required
          rows={6}
          placeholder="공지 내용"
          onChange={(e) => setForm({ ...form, content: e.target.value })}
        />
        <div>
          <button className="button button-dark" disabled={busy}>
            {form.id ? "수정 저장" : "공지 등록"}
          </button>
          {form.id && (
            <button
              type="button"
              onClick={() => setForm({ id: null, title: "", content: "" })}
            >
              수정 취소
            </button>
          )}
        </div>
      </form>
      <div className="admin-notices">
        {notices?.content?.map((post) => (
          <article key={post.id}>
            <div>
              <strong>{post.title}</strong>
              <small>
                {new Date(post.createdAt).toLocaleDateString("ko-KR")}
              </small>
            </div>
            <div>
              <button
                onClick={() =>
                  setForm({
                    id: post.id,
                    title: post.title,
                    content: post.content,
                  })
                }
              >
                수정
              </button>
              <button onClick={() => remove(post)}>삭제</button>
            </div>
          </article>
        ))}
      </div>
    </>
  );
}

function QnaSettings({
  questions,
  selected,
  setSelected,
  comments,
  answer,
  setAnswer,
  busy,
  submit,
}) {
  return (
    <>
      <h2>Q&A</h2>
      <p className="admin-section-help">
        질문을 선택해 내용을 확인하고 관리자 답변을 등록하세요.
      </p>
      <div className="admin-qna-layout">
        <div className="admin-question-list">
          {questions?.content?.map((question) => (
            <button
              className={selected?.id === question.id ? "active" : ""}
              key={question.id}
              onClick={() => setSelected(question)}
            >
              <strong>{question.title}</strong>
              <small>
                {new Date(question.createdAt).toLocaleDateString("ko-KR")}
              </small>
            </button>
          ))}
        </div>
        <div className="admin-question-detail">
          {selected ? (
            <>
              <h3>{selected.title}</h3>
              <p className="question-body">{selected.content}</p>
              <div className="admin-answers">
                {comments.map((comment) => (
                  <article key={comment.id}>
                    <strong>{comment.authorName}</strong>
                    <small>
                      {new Date(comment.createdAt).toLocaleString("ko-KR")}
                    </small>
                    <p>{comment.content}</p>
                  </article>
                ))}
              </div>
              <form className="admin-answer-form" onSubmit={submit}>
                <textarea
                  value={answer}
                  required
                  maxLength={3000}
                  rows={5}
                  placeholder="관리자 답변을 작성하세요."
                  onChange={(e) => setAnswer(e.target.value)}
                />
                <button className="button button-dark" disabled={busy}>
                  답변 등록
                </button>
              </form>
            </>
          ) : (
            <div className="admin-empty">왼쪽에서 질문을 선택하세요.</div>
          )}
        </div>
      </div>
    </>
  );
}
