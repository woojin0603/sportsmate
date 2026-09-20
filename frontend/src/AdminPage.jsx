import { useEffect, useState } from "react";
import { api, json } from "./api";
import { formatPhone } from "./phone";

// 관리자에게 운영 현황, 회원 권한, 공지사항 관리 기능을 제공한다.
export default function AdminPage({ notify }) {
  const [overview, setOverview] = useState(null);
  const [users, setUsers] = useState(null);
  const [notices, setNotices] = useState(null);
  const [page, setPage] = useState(0);
  const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ id: null, title: "", content: "" });

  // 현재 페이지에 필요한 관리 데이터만 다시 읽는다.
  useEffect(() => {
    let active = true;
    Promise.all([
      api("/api/admin/overview"),
      api(`/api/admin/users?page=${page}&size=15`),
      api("/api/notices?page=0&size=50"),
    ])
      .then(([summary, members, posts]) => {
        if (!active) return;
        setOverview(summary);
        setUsers(members);
        setNotices(posts);
        setError("");
      })
      .catch((failure) => {
        if (active) setError(failure.message);
      });
    return () => {
      active = false;
    };
  }, [page, refresh]);

  // 선택한 회원의 권한을 서버에 저장하고 목록을 새로고침한다.
  async function changeRole(member, role) {
    try {
      await api(`/api/admin/users/${member.id}/role`, {
        method: "PATCH",
        body: json({ role }),
      });
      notify(
        `${member.username} 권한을 ${role === "ADMIN" ? "관리자" : "일반 사용자"}로 변경했습니다.`,
      );
      setRefresh((value) => value + 1);
    } catch (failure) {
      notify(failure.message, "error");
    }
  }

  // 공지사항을 새로 작성하거나 선택한 공지를 수정한다.
  async function saveNotice(event) {
    event.preventDefault();
    setBusy(true);
    try {
      await api(
        form.id ? `/api/admin/notices/${form.id}` : "/api/admin/notices",
        {
          method: form.id ? "PUT" : "POST",
          body: json({ title: form.title, content: form.content }),
        },
      );
      notify(form.id ? "공지를 수정했습니다." : "공지를 등록했습니다.");
      setForm({ id: null, title: "", content: "" });
      setRefresh((value) => value + 1);
    } catch (failure) {
      notify(failure.message, "error");
    } finally {
      setBusy(false);
    }
  }

  // 실수로 공지를 지우지 않도록 확인한 뒤 삭제한다.
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

  return (
    <>
      <div className="page-intro intro-account">
        <div>
          <span className="eyebrow">ADMIN DASHBOARD</span>
          <h1>서비스 관리</h1>
          <p>운영 현황을 보고 회원 권한과 공지사항을 관리하세요.</p>
        </div>
      </div>
      {error && <div className="form-error">{error}</div>}
      <section className="page-section">
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
      </section>
      <section className="page-section">
        <h2>회원 권한</h2>
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
                      aria-label={`${member.username} 권한`}
                      value={member.role}
                      disabled={member.username === "admin"}
                      onChange={(event) =>
                        changeRole(member, event.target.value)
                      }
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
        <p className="admin-note">
          관리자 권한 해제는 즉시 적용되고, 새 관리자 권한은 해당 회원이 다시
          로그인하면 적용됩니다.
        </p>
      </section>
      <section className="page-section">
        <h2>공지사항 관리</h2>
        <form className="admin-notice-form" onSubmit={saveNotice}>
          <input
            value={form.title}
            maxLength={200}
            required
            placeholder="공지 제목"
            onChange={(event) =>
              setForm({ ...form, title: event.target.value })
            }
          />
          <textarea
            value={form.content}
            maxLength={5000}
            required
            rows={5}
            placeholder="공지 내용"
            onChange={(event) =>
              setForm({ ...form, content: event.target.value })
            }
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
                <button onClick={() => deleteNotice(post)}>삭제</button>
              </div>
            </article>
          ))}
        </div>
      </section>
    </>
  );
}
