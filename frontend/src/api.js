let csrfToken = null;

// Spring API 호출 시 쿠키 인증과 변경 요청용 CSRF 토큰을 함께 관리한다.
export async function api(path, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  if (!["GET", "HEAD", "OPTIONS"].includes(method) && path !== "/api/csrf") {
    if (!csrfToken) {
      const csrf = await api("/api/csrf");
      csrfToken = csrf.token;
    }
  }
  const headers = { ...options.headers };
  if (options.body && !(options.body instanceof FormData))
    headers["Content-Type"] = "application/json";
  if (!["GET", "HEAD", "OPTIONS"].includes(method) && path !== "/api/csrf")
    headers["X-XSRF-TOKEN"] = csrfToken;
  const response = await fetch(path, {
    credentials: "include",
    ...options,
    headers,
  });
  if (response.status === 204) return null;
  const contentType = response.headers.get("content-type") || "";
  const data = contentType.includes("json")
    ? await response.json()
    : await response.text();
  if (!response.ok) {
    const error = new Error(
      data?.message || `요청을 처리하지 못했습니다. (${response.status})`,
    );
    error.status = response.status;
    throw error;
  }
  return data;
}

// 값이 있는 검색 조건만 URL 쿼리 문자열에 넣는다.
export function query(path, values) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== "" && value !== undefined && value !== null)
      params.set(key, value);
  });
  return `${path}${params.size ? `?${params}` : ""}`;
}

// JSON 요청 본문을 직렬화한다.
export function json(body) {
  return JSON.stringify(body);
}

// 로그아웃 이후 다음 변경 요청에서 새 CSRF 토큰을 받도록 초기화한다.
export function resetCsrf() {
  csrfToken = null;
}
