import { useCallback, useEffect, useMemo, useState } from "react";
import {
  MapContainer,
  Marker,
  TileLayer,
  Tooltip,
  useMap,
} from "react-leaflet";
import L from "leaflet";
import {
  ArrowDownRight,
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  BadgeCheck,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  Clock3,
  Dumbbell,
  Footprints,
  HeartPulse,
  LayoutDashboard,
  LogIn,
  LogOut,
  MapPin,
  Menu,
  MessageCircle,
  Navigation,
  Search,
  Send,
  ShieldCheck,
  Sparkles,
  Star,
  Ticket,
  UserRound,
  Users,
  Waves,
  X,
} from "lucide-react";
import { api, json, query, resetCsrf } from "./api";
import FitnessPage from "./FitnessPage.jsx";
import AdminPage from "./AdminPage.jsx";
import { formatPhone } from "./phone";

const navItems = [
  { label: "시설 찾기", path: "/", icon: MapPin },
  { label: "프로그램", path: "/programs", icon: Dumbbell },
  { label: "맞춤 운동", path: "/recommendations", icon: HeartPulse },
  { label: "나의 체력", path: "/fitness", icon: HeartPulse },
  { label: "체력측정센터", path: "/fitness-centers", icon: MapPin },
  { label: "내 예약", path: "/reservations", icon: Ticket },
  { label: "커뮤니티", path: "/community", icon: MessageCircle },
];

// 공공데이터포털 시설 API의 cp_nm 값과 일치하는 17개 시·도 이름이다.
const provinces = [
  "서울특별시",
  "부산광역시",
  "대구광역시",
  "인천광역시",
  "광주광역시",
  "대전광역시",
  "울산광역시",
  "세종특별자치시",
  "경기도",
  "강원특별자치도",
  "충청북도",
  "충청남도",
  "전북특별자치도",
  "전라남도",
  "경상북도",
  "경상남도",
  "제주특별자치도",
];

const iconByType = (type = "") => {
  if (type.includes("수영")) return Waves;
  if (type.includes("걷") || type.includes("운동장")) return Footprints;
  return Dumbbell;
};

const currency = (value) =>
  value == null
    ? "요금 문의"
    : Number(value) === 0
      ? "무료"
      : `${Number(value).toLocaleString("ko-KR")}원`;
const date = (value) =>
  value
    ? new Date(value).toLocaleDateString("ko-KR", {
        year: "numeric",
        month: "long",
        day: "numeric",
      })
    : "일정 확인 필요";
const shortDate = (value) =>
  value
    ? new Date(value).toLocaleDateString("ko-KR", {
        month: "short",
        day: "numeric",
      })
    : "—";
const statusText = {
  REQUESTED: "신청 기록",
  CONFIRMED: "예약 확정",
  CANCELLED: "취소 완료",
};
const pageList = (data) => data?.content || [];

// 브라우저 주소와 화면 경로를 동기화하고 내부 이동 함수를 제공한다.
function useRoute() {
  const [path, setPath] = useState(window.location.pathname);
  useEffect(() => {
    const onPop = () => setPath(window.location.pathname);
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);
  const navigate = useCallback((next) => {
    if (next !== window.location.pathname)
      window.history.pushState({}, "", next);
    setPath(next);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, []);
  return [path, navigate];
}

// API 요청의 로딩·오류·결과 상태를 관리하고 재조회 함수를 제공한다.
function useRemote(path, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: "" });
  const load = useCallback(async () => {
    if (!path) {
      setState({ data: null, loading: false, error: "" });
      return;
    }
    setState((previous) => ({ ...previous, loading: true, error: "" }));
    try {
      setState({ data: await api(path), loading: false, error: "" });
    } catch (error) {
      setState({ data: null, loading: false, error: error.message });
    }
  }, [path]);
  useEffect(() => {
    load();
  }, [load, ...deps]); // eslint-disable-line react-hooks/exhaustive-deps
  return { ...state, reload: load };
}

// 조회 결과가 없거나 로그인이 필요한 상태를 안내한다.
function Empty({ icon: Icon = CircleHelp, title, description, action }) {
  return (
    <div className="empty-state">
      <span className="empty-icon">
        <Icon size={27} />
      </span>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}

// 자료를 가져오는 동안 카드 형태의 로딩 표시를 보여준다.
function LoadingCards({ count = 3 }) {
  return (
    <div className="skeleton-grid">
      {Array.from({ length: count }, (_, i) => (
        <div className="skeleton-card" key={i}>
          <div />
          <span />
          <span />
        </div>
      ))}
    </div>
  );
}

// API 호출 실패 원인과 재시도 버튼을 보여준다.
function PageError({ message, retry }) {
  const hint = message.includes("공공데이터 API URL/키")
    ? "Spring 설정에 체육시설 API 인증키를 넣고 서버를 다시 실행해 주세요."
    : "Spring Boot 서버가 실행 중인지 확인해 주세요.";
  return (
    <Empty
      icon={CircleHelp}
      title="데이터를 불러오지 못했어요"
      description={`${message} · ${hint}`}
      action={
        <button className="button button-dark" onClick={retry}>
          다시 시도
        </button>
      }
    />
  );
}

// 화면 영역의 제목과 보조 설명을 공통 형식으로 표시한다.
function SectionTitle({ eyebrow, title, subtitle, aside }) {
  return (
    <div className="section-heading">
      <div>
        <span className="eyebrow">{eyebrow}</span>
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {aside}
    </div>
  );
}

// 목록의 이전·다음 페이지 이동을 처리한다.
function Pagination({ page, onPage }) {
  if (!page || page.totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button
        disabled={page.number === 0}
        onClick={() => onPage(page.number - 1)}
      >
        <ChevronLeft size={17} />
      </button>
      <span>
        {page.number + 1} / {page.totalPages}
      </span>
      <button
        disabled={page.number + 1 >= page.totalPages}
        onClick={() => onPage(page.number + 1)}
      >
        <ChevronRight size={17} />
      </button>
    </div>
  );
}

// 현재 화면 제목과 회원 로그인 상태를 상단에 표시한다.
function Header({
  path,
  navigate,
  user,
  openAuth,
  openSignup,
  onLogout,
  mobileMenu,
  setMobileMenu,
}) {
  const section =
    path === "/" || path.startsWith("/facilities/")
      ? "시설 찾기"
      : path === "/admin"
        ? "관리자"
        : navItems.find(
            (item) => path.startsWith(item.path) && item.path !== "/",
          )?.label || "마이페이지";
  return (
    <header className="topbar">
      <button
        className="mobile-menu"
        onClick={() => setMobileMenu(!mobileMenu)}
        aria-label="메뉴 열기"
      >
        <Menu size={22} />
      </button>
      <div className="breadcrumb">
        <span>SportMap</span>
        <ChevronRight size={14} />
        <strong>{section}</strong>
      </div>
      <div className="topbar-right">
        <span className="today-label">
          <CalendarDays size={16} />{" "}
          {new Date().toLocaleDateString("ko-KR", {
            month: "long",
            day: "numeric",
            weekday: "short",
          })}
        </span>
        {user ? (
          <button className="user-chip" onClick={() => navigate("/account")}>
            <span>{user.fullName?.charAt(0)}</span>
            {user.fullName}님
          </button>
        ) : (
          <>
            <button className="button button-dark compact" onClick={openAuth}>
              <LogIn size={16} /> 로그인
            </button>
            <button
              className="button button-signup compact"
              onClick={openSignup}
            >
              <UserRound size={16} /> 회원가입
            </button>
          </>
        )}
        {user && (
          <button
            className="icon-button logout-desktop"
            title="로그아웃"
            onClick={onLogout}
          >
            <LogOut size={18} />
          </button>
        )}
      </div>
    </header>
  );
}

// 주요 화면 이동 메뉴와 모바일 메뉴 상태를 관리한다.
function Sidebar({
  path,
  navigate,
  user,
  openAuth,
  mobileMenu,
  setMobileMenu,
}) {
  const go = (destination) => {
    navigate(destination);
    setMobileMenu(false);
  };
  return (
    <>
      <div
        className={`mobile-shade ${mobileMenu ? "show" : ""}`}
        onClick={() => setMobileMenu(false)}
      />
      <aside className={`sidebar ${mobileMenu ? "open" : ""}`}>
        <div
          className="brand"
          onClick={() => go("/")}
          role="button"
          tabIndex={0}
        >
          <span className="brand-mark">
            <Navigation size={21} fill="currentColor" />
          </span>
          <div>
            <strong>
              sport<span>map</span>
            </strong>
            <small>MOVE YOUR WAY</small>
          </div>
        </div>
        <div className="nav-label">EXPLORE</div>
        <nav className="side-nav">
          {navItems.map(({ label, path: destination, icon: Icon }) => (
            <button
              key={destination}
              className={
                (
                  destination === "/"
                    ? path === "/" || path.startsWith("/facilities/")
                    : path.startsWith(destination)
                )
                  ? "active"
                  : ""
              }
              onClick={() => go(destination)}
            >
              <Icon size={19} />
              <span>{label}</span>
              <ChevronRight className="nav-arrow" size={15} />
            </button>
          ))}
        </nav>
        <div className="sidebar-divider" />
        <div className="nav-label">ACCOUNT</div>
        <nav className="side-nav">
          <button
            className={path === "/account" ? "active" : ""}
            onClick={() => (user ? go("/account") : openAuth())}
          >
            <UserRound size={19} />
            <span>마이페이지</span>
            <ChevronRight className="nav-arrow" size={15} />
          </button>
          {user?.role === "ADMIN" && (
            <button
              className={path === "/admin" ? "active" : ""}
              onClick={() => go("/admin")}
            >
              <ShieldCheck size={19} />
              <span>관리자</span>
              <ChevronRight className="nav-arrow" size={15} />
            </button>
          )}
        </nav>
        <div className="sidebar-spacer" />
        <div className="sidebar-promo">
          <span>
            <Sparkles size={17} /> FOR A BETTER YOU
          </span>
          <h3>
            오늘의 움직임이
            <br />
            내일의 나를 바꿔요.
          </h3>
          <p>내 주변 운동 기회를 찾아보세요.</p>
          <button onClick={() => go("/recommendations")}>
            맞춤 운동 보기 <ArrowRight size={16} />
          </button>
        </div>
        <div className="sidebar-bottom">
          <span className="online-dot" /> 공공데이터로 더 가까워진 운동
        </div>
      </aside>
    </>
  );
}

// 시설·프로그램 수를 포함한 첫 화면 안내 영역을 표시한다.
function Hero({ navigate, facilityCount, programCount }) {
  return (
    <section className="hero">
      <div className="hero-content">
        <div className="hero-pill">
          <span /> YOUR ACTIVE CITY
        </div>
        <h1>
          운동하고 싶은 오늘,
          <br />
          <em>가까운 곳</em>에서 시작하세요.
        </h1>
        <p>
          내 주변 체육시설과 프로그램을 한눈에.
          <br />
          나에게 맞는 움직임을 발견해 보세요.
        </p>
        <div className="hero-actions">
          <button
            className="button button-lime"
            onClick={() =>
              document
                .getElementById("facility-list")
                ?.scrollIntoView({ behavior: "smooth" })
            }
          >
            시설 둘러보기 <ArrowUpRight size={18} />
          </button>
          <button
            className="button button-ghost"
            onClick={() => navigate("/recommendations")}
          >
            나에게 맞는 운동 <ArrowRight size={17} />
          </button>
        </div>
      </div>
      <div className="hero-art" aria-hidden="true">
        <div className="hero-orbit orbit-one" />
        <div className="hero-orbit orbit-two" />
        <div className="hero-orbit orbit-three" />
        <div className="hero-pin pin-one">
          <Waves size={24} />
        </div>
        <div className="hero-pin pin-two">
          <Dumbbell size={23} />
        </div>
        <div className="hero-pin pin-three">
          <Footprints size={23} />
        </div>
        <div className="hero-float">
          <span className="hero-float-icon">
            <MapPin size={18} />
          </span>
          <div>
            <small>지금 가까이 있는</small>
            <strong>나의 운동 공간</strong>
          </div>
          <ArrowUpRight size={18} />
        </div>
      </div>
      <div className="hero-counter">
        <span>
          <strong>{facilityCount ?? "—"}</strong>개의 시설
        </span>
        <i />
        <span>
          <strong>{programCount ?? "—"}</strong>개의 프로그램
        </span>
      </div>
    </section>
  );
}

// 시설 정보를 요약하고 선택 시 상세 화면을 연다.
function FacilityCard({ item, onOpen, featured = false }) {
  const Icon = iconByType(item.type);
  const tone =
    [...String(item.id)].reduce(
      (sum, letter) => sum + letter.charCodeAt(0),
      0,
    ) % 4;
  return (
    <button
      className={`facility-card ${featured ? "featured" : ""}`}
      onClick={() => onOpen(item.id)}
    >
      <div className={`facility-visual tone-${tone}`}>
        <div className="visual-pattern" />
        <span className="facility-type-icon">
          <Icon size={26} />
        </span>
        <span className="visual-tag">
          {item.publicFacility ? "공공시설" : "체육시설"}
        </span>
      </div>
      <div className="facility-card-body">
        <div className="facility-card-meta">
          <span>{item.type || "체육시설"}</span>
          <span>
            <MapPin size={12} /> {item.regionName || "지역 정보"}
          </span>
        </div>
        <h3>{item.name}</h3>
        <p>{item.roadAddress || "주소 정보가 준비 중입니다."}</p>
        <div className="facility-card-footer">
          <span>
            {item.reservable ? (
              <>
                <BadgeCheck size={14} /> 예약 가능
              </>
            ) : (
              <>
                <Navigation size={14} /> 시설 정보 보기
              </>
            )}
          </span>
          <span className="card-arrow">
            <ArrowUpRight size={18} />
          </span>
        </div>
      </div>
    </button>
  );
}

const markerIcon = L.divIcon({
  className: "map-marker",
  html: '<span class="map-marker-inner">●</span>',
  iconSize: [30, 30],
  iconAnchor: [15, 30],
});
const koreaCenter = [36.35, 127.8];
const koreaBounds = [
  [32.5, 123.5],
  [39.2, 132.2],
];
// 공공데이터의 좌표가 대한민국 영역에 있는지 확인한다.
const isKoreaCoordinate = (item) => {
  const latitude = Number(item.latitude);
  const longitude = Number(item.longitude);
  return (
    Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= koreaBounds[0][0] &&
    latitude <= koreaBounds[1][0] &&
    longitude >= koreaBounds[0][1] &&
    longitude <= koreaBounds[1][1]
  );
};
// 조회된 시설 좌표에 맞춰 지도 중심과 확대 수준을 조정한다.
function MapFocus({ items }) {
  const map = useMap();
  useEffect(() => {
    const points = items
      .filter(isKoreaCoordinate)
      .map((item) => [Number(item.latitude), Number(item.longitude)]);
    if (points.length === 1) map.setView(points[0], 13);
    else if (points.length > 1)
      map.fitBounds(points, { padding: [38, 38], maxZoom: 12, animate: false });
    else map.setView(koreaCenter, 7, { animate: false });
  }, [items, map]);
  return null;
}
// 좌표가 있는 시설을 지도에 표시하고 마커 선택을 전달한다.
function FacilityMap({ items, onOpen, compact = false }) {
  const mapped = items.filter(isKoreaCoordinate);
  return (
    <div className={`map-panel ${compact ? "compact-map" : ""}`}>
      <div className="map-header">
        <div>
          <span className="map-live">
            <span /> LIVE MAP
          </span>
          <h3>지도로 둘러보기</h3>
        </div>
        <span className="map-count">{mapped.length}개 위치 표시</span>
      </div>
      <div className="map-body">
        <MapContainer
          center={koreaCenter}
          zoom={7}
          minZoom={6}
          maxBounds={koreaBounds}
          maxBoundsViscosity={1}
          scrollWheelZoom={false}
          className="leaflet-map"
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <MapFocus items={mapped} />
          {mapped.map((item) => (
            <Marker
              key={item.id}
              position={[Number(item.latitude), Number(item.longitude)]}
              icon={markerIcon}
              eventHandlers={{ click: () => onOpen(item.id) }}
            >
              <Tooltip direction="top" offset={[0, -22]}>
                {item.name}
              </Tooltip>
            </Marker>
          ))}
        </MapContainer>
        {mapped.length === 0 && (
          <div className="map-overlay">표시할 시설 좌표가 없습니다.</div>
        )}
      </div>
      <div className="map-footer">
        <MapPin size={16} /> 마커를 누르면 시설 정보를 볼 수 있어요.
      </div>
    </div>
  );
}

// 로그인한 사용자의 이번 달 예약일과 가까운 일정을 메인 화면에 표시한다.
function HomeReservationCalendar({ user, navigate, openAuth }) {
  const [month, setMonth] = useState(() => {
    const today = new Date();
    return new Date(today.getFullYear(), today.getMonth(), 1);
  });
  const reservations = useRemote(user ? "/api/reservations?size=100" : null);
  const [programMap, setProgramMap] = useState({});
  const activeReservations = pageList(reservations.data).filter(
    (item) => item.status !== "CANCELLED" && item.startsAt,
  );

  useEffect(() => {
    if (!user || activeReservations.length === 0) {
      setProgramMap({});
      return;
    }
    const ids = [...new Set(activeReservations.map((item) => item.programId))];
    Promise.all(
      ids.map((id) =>
        api(`/api/programs/${id}`)
          .then((program) => [id, program])
          .catch(() => [id, null]),
      ),
    ).then((entries) => setProgramMap(Object.fromEntries(entries)));
  }, [user, reservations.data]); // eslint-disable-line react-hooks/exhaustive-deps

  const firstDay = new Date(month.getFullYear(), month.getMonth(), 1);
  const calendarStart = new Date(firstDay);
  calendarStart.setDate(1 - firstDay.getDay());
  const days = Array.from({ length: 42 }, (_, index) => {
    const day = new Date(calendarStart);
    day.setDate(calendarStart.getDate() + index);
    return day;
  });
  const dateKey = (value) => {
    const target = new Date(value);
    return `${target.getFullYear()}-${target.getMonth()}-${target.getDate()}`;
  };
  const reservedDates = new Set(
    activeReservations.map((item) => dateKey(item.startsAt)),
  );
  const upcoming = [...activeReservations]
    .filter(
      (item) => new Date(item.startsAt) >= new Date().setHours(0, 0, 0, 0),
    )
    .sort((left, right) => new Date(left.startsAt) - new Date(right.startsAt))
    .slice(0, 3);

  return (
    <section className="home-calendar-panel">
      <div className="home-calendar-header">
        <div>
          <span className="map-live">
            <span /> MY SCHEDULE
          </span>
          <h3>예약 캘린더</h3>
        </div>
        {user && (
          <button onClick={() => navigate("/reservations")}>전체 일정</button>
        )}
      </div>
      {!user ? (
        <div className="home-calendar-empty">
          <CalendarDays size={28} />
          <p>로그인하고 예약 일정을 확인하세요.</p>
          <button onClick={openAuth}>로그인</button>
        </div>
      ) : reservations.loading ? (
        <div className="home-calendar-empty">일정을 불러오는 중입니다.</div>
      ) : reservations.error ? (
        <div className="home-calendar-empty">
          예약 일정을 불러오지 못했습니다.
        </div>
      ) : (
        <>
          <div className="calendar-month-nav">
            <button
              aria-label="이전 달"
              onClick={() =>
                setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))
              }
            >
              <ChevronLeft size={17} />
            </button>
            <strong>
              {month.getFullYear()}년 {month.getMonth() + 1}월
            </strong>
            <button
              aria-label="다음 달"
              onClick={() =>
                setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))
              }
            >
              <ChevronRight size={17} />
            </button>
          </div>
          <div className="calendar-weekdays">
            {["일", "월", "화", "수", "목", "금", "토"].map((weekday) => (
              <span key={weekday}>{weekday}</span>
            ))}
          </div>
          <div className="calendar-days">
            {days.map((day) => {
              const reserved = reservedDates.has(dateKey(day));
              const outside = day.getMonth() !== month.getMonth();
              const today = dateKey(day) === dateKey(new Date());
              return (
                <span
                  key={day.toISOString()}
                  className={`${outside ? "outside" : ""} ${today ? "today" : ""} ${reserved ? "reserved" : ""}`}
                  title={reserved ? "예약 일정이 있습니다" : undefined}
                >
                  {day.getDate()}
                </span>
              );
            })}
          </div>
          <div className="calendar-upcoming">
            {upcoming.length ? (
              upcoming.map((item) => (
                <button key={item.id} onClick={() => navigate("/reservations")}>
                  <span>{shortDate(item.startsAt)}</span>
                  <strong>
                    {programMap[item.programId]?.name ||
                      `프로그램 ${item.programId}`}
                  </strong>
                </button>
              ))
            ) : (
              <p>예정된 예약이 없습니다.</p>
            )}
          </div>
        </>
      )}
    </section>
  );
}

// 메인 화면에서도 공공데이터포털의 최신 시설을 지역별로 탐색한다.
function FacilitiesPage({ navigate, user, openAuth }) {
  const [keyword, setKeyword] = useState("");
  const [submitted, setSubmitted] = useState("");
  const [province, setProvince] = useState("");
  const [city, setCity] = useState("");
  const [submittedCity, setSubmittedCity] = useState("");
  const [locality, setLocality] = useState("");
  const [selectedExternal, setSelectedExternal] = useState(null);
  const [page, setPage] = useState(0);
  const livePath = locality
    ? query("/api/facilities/external/by-locality", {
        cp_nm: province,
        cpb_nm: submittedCity,
        addr_emd_nm: locality,
        faci_nm: submitted,
        page,
        size: 9,
      })
    : query("/api/facilities/external", {
        pageNo: page + 1,
        numOfRows: 9,
        faci_nm: submitted,
        cp_nm: province,
        cpb_nm: submittedCity,
      });
  const facilities = useRemote(livePath);
  const localities = useRemote(
    submittedCity
      ? query("/api/facilities/external/localities", {
          cp_nm: province,
          cpb_nm: submittedCity,
        })
      : null,
  );
  const programs = useRemote("/api/programs?size=1");
  const liveBody = facilities.data?.response?.body;
  const liveRaw = locality
    ? facilities.data?.content || []
    : liveBody?.items?.item || [];
  const items = (Array.isArray(liveRaw) ? liveRaw : [liveRaw])
    .filter(Boolean)
    .map(normalizeLiveFacility);
  const total = Number(
    locality
      ? facilities.data?.page?.totalElements || 0
      : liveBody?.totalCount || 0,
  );
  const pageInfo = {
    number: page,
    totalPages: locality
      ? facilities.data?.page?.totalPages || 0
      : Math.ceil(total / 9),
  };
  const openFacility = (id) => {
    setSelectedExternal(items.find((item) => item.id === id) || null);
  };
  const submit = (event) => {
    event.preventDefault();
    setPage(0);
    setSubmitted(keyword.trim());
    if (city.trim() !== submittedCity) setLocality("");
    setSubmittedCity(city.trim());
  };
  return (
    <>
      <Hero
        navigate={navigate}
        facilityCount={total}
        programCount={programs.data?.page?.totalElements}
      />
      <div className="quick-stats">
        <div>
          <span className="stat-icon peach">
            <MapPin size={20} />
          </span>
          <div>
            <strong>{total}</strong>
            <span>찾을 수 있는 시설</span>
          </div>
          <ArrowUpRight size={18} />
        </div>
        <div>
          <span className="stat-icon mint">
            <Dumbbell size={20} />
          </span>
          <div>
            <strong>{programs.data?.page?.totalElements ?? "—"}</strong>
            <span>운영 프로그램</span>
          </div>
          <ArrowUpRight size={18} />
        </div>
        <div>
          <span className="stat-icon lilac">
            <HeartPulse size={20} />
          </span>
          <div>
            <strong>내게 맞게</strong>
            <span>운동 추천받기</span>
          </div>
          <button onClick={() => navigate("/recommendations")}>
            <ArrowRight size={18} />
          </button>
        </div>
      </div>
      <section id="facility-list" className="page-section">
        <SectionTitle
          eyebrow="DISCOVER PLACES"
          title="어디서 운동할까요?"
          subtitle="지역과 시설 종류를 살펴보고, 마음에 드는 곳을 찾아보세요."
          aside={
            <button
              className="button button-dark"
              onClick={() => navigate("/live-facilities")}
            >
              공공 API 실시간 조회 <ArrowUpRight size={16} />
            </button>
          }
        />
        <form className="filter-bar live-filter" onSubmit={submit}>
          <label className="select-wrap">
            <MapPin size={17} />
            <select
              value={province}
              aria-label="시도 선택"
              onChange={(event) => {
                setProvince(event.target.value);
                setCity("");
                setSubmittedCity("");
                setLocality("");
                setPage(0);
              }}
            >
              <option value="">전체 시·도</option>
              {provinces.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
            <ChevronDown size={15} />
          </label>
          <div className="search-field">
            <input
              value={city}
              disabled={!province}
              onChange={(event) => setCity(event.target.value)}
              placeholder="시·군·구 (예: 강남구)"
              aria-label="시군구"
            />
          </div>
          <label className="select-wrap">
            <select
              value={locality}
              disabled={!submittedCity || localities.loading}
              aria-label="하위구역 선택"
              onChange={(event) => {
                setLocality(event.target.value);
                setPage(0);
              }}
            >
              <option value="">
                {localities.loading ? "하위구역 불러오는 중" : "전체 하위구역"}
              </option>
              {(Array.isArray(localities.data) ? localities.data : []).map(
                (name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ),
              )}
            </select>
            <ChevronDown size={15} />
          </label>
          <div className="search-field">
            <Search size={19} />
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="시설명 검색"
              aria-label="시설 검색"
            />
            <button type="submit">검색</button>
          </div>
        </form>
        {localities.error && (
          <p className="form-error">
            하위구역을 불러오지 못했습니다: {localities.error}
          </p>
        )}
        <p className="muted">
          공공 API 자료입니다. 시·군·구를 입력하고 검색하면 하위구역을 선택할 수
          있습니다.
        </p>
        <div className="explore-layout">
          <div className="facility-results">
            <div className="results-header">
              <strong>
                검색 결과 <em>{total.toLocaleString("ko-KR")}</em>
              </strong>
              <span>공공데이터포털 시설 API</span>
            </div>
            {facilities.loading ? (
              <LoadingCards />
            ) : facilities.error ? (
              <PageError message={facilities.error} retry={facilities.reload} />
            ) : items.length ? (
              <>
                <div className="facility-grid">
                  {items.map((item) => (
                    <FacilityCard
                      key={item.id}
                      item={item}
                      onOpen={openFacility}
                    />
                  ))}
                </div>
                <Pagination page={pageInfo} onPage={setPage} />
              </>
            ) : (
              <Empty
                icon={Search}
                title="검색 결과가 없어요"
                description="다른 키워드나 지역으로 다시 찾아보세요."
              />
            )}
          </div>
          <aside className="facility-side-column">
            <FacilityMap items={items} onOpen={openFacility} compact />
            <HomeReservationCalendar
              user={user}
              navigate={navigate}
              openAuth={openAuth}
            />
          </aside>
        </div>
      </section>
      {selectedExternal && (
        <ExternalFacilityModal
          item={selectedExternal}
          close={() => setSelectedExternal(null)}
        />
      )}
      <section className="bottom-banner">
        <div>
          <span className="eyebrow">FIND YOUR NEXT MOVE</span>
          <h2>
            운동을 시작할 이유는
            <br />
            생각보다 가까이에 있어요.
          </h2>
          <p>다양한 체육 프로그램에서 오늘의 나에게 맞는 시간을 찾아보세요.</p>
        </div>
        <button
          className="button button-dark"
          onClick={() => navigate("/programs")}
        >
          프로그램 살펴보기 <ArrowUpRight size={18} />
        </button>
        <div className="banner-orb" />
      </section>
    </>
  );
}

// 공공데이터포털 시설 API의 원본 응답을 화면 카드와 지도에 필요한 값으로 변환한다.
function normalizeLiveFacility(row) {
  return {
    id: row.faci_cd || `row-${row.row_num}`,
    name: row.faci_nm || "이름 없는 시설",
    roadAddress: row.faci_road_addr || row.faci_addr || "",
    type: row.ftype_nm || row.fcob_nm || "",
    regionName: [row.cp_nm, row.cpb_nm, row.addr_emd_nm]
      .filter(Boolean)
      .join(" "),
    latitude: row.faci_lat || null,
    longitude: row.faci_lot || null,
    publicFacility: row.faci_gb_nm === "공공",
    status: row.faci_stat_nm || "운영상태 정보 없음",
    category: row.faci_gb_nm || "체육시설",
    phone: row.faci_tel_no || "",
  };
}

// 공공 API 시설은 로컬 DB에 없는 경우에도 주소와 운영 상태를 바로 확인할 수 있다.
function ExternalFacilityModal({ item, close }) {
  return (
    <div className="modal-backdrop" onMouseDown={close}>
      <div
        className="modal dialog-modal"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button className="modal-close" onClick={close} aria-label="닫기">
          <X size={20} />
        </button>
        <span className="eyebrow">PUBLIC DATA FACILITY</span>
        <h2>{item.name}</h2>
        <p>{item.roadAddress || "주소 정보 없음"}</p>
        <div className="info-list">
          <div>
            <span>시설 유형</span>
            <strong>{item.type || "정보 없음"}</strong>
          </div>
          <div>
            <span>지역</span>
            <strong>{item.regionName || "정보 없음"}</strong>
          </div>
          <div>
            <span>운영 상태</span>
            <strong>{item.status}</strong>
          </div>
          <div>
            <span>시설 구분</span>
            <strong>{item.category}</strong>
          </div>
          <div>
            <span>문의 전화</span>
            <strong>
              {item.phone ? formatPhone(item.phone) : "정보 없음"}
            </strong>
          </div>
        </div>
      </div>
    </div>
  );
}

// 인증키를 서버에만 보관한 채 시설 목록을 공공데이터 API에서 실시간으로 가져온다.
function LiveFacilitiesPage({ navigate }) {
  const [keyword, setKeyword] = useState("");
  const [submitted, setSubmitted] = useState("");
  const [province, setProvince] = useState("");
  const [city, setCity] = useState("");
  const [submittedCity, setSubmittedCity] = useState("");
  const [locality, setLocality] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const path = locality
    ? query("/api/facilities/external/by-locality", {
        cp_nm: province,
        cpb_nm: submittedCity,
        addr_emd_nm: locality,
        faci_nm: submitted,
        page,
        size: 12,
      })
    : query("/api/facilities/external", {
        pageNo: page + 1,
        numOfRows: 12,
        faci_nm: submitted,
        cp_nm: province,
        cpb_nm: submittedCity,
      });
  const result = useRemote(path);
  const localities = useRemote(
    submittedCity
      ? query("/api/facilities/external/localities", {
          cp_nm: province,
          cpb_nm: submittedCity,
        })
      : null,
  );
  const body = result.data?.response?.body;
  const raw = locality ? result.data?.content || [] : body?.items?.item || [];
  const items = (Array.isArray(raw) ? raw : [raw]).map(normalizeLiveFacility);
  const total = Number(
    locality ? result.data?.page?.totalElements || 0 : body?.totalCount || 0,
  );
  const pages = locality
    ? result.data?.page?.totalPages || 0
    : Math.ceil(total / 12);
  const open = (id) =>
    setSelected(items.find((item) => item.id === id) || null);

  // 검색 입력을 확정하고 첫 페이지부터 다시 조회한다.
  function submit(event) {
    event.preventDefault();
    setPage(0);
    if (city.trim() !== submittedCity) setLocality("");
    setSubmitted(keyword.trim());
    setSubmittedCity(city.trim());
  }

  return (
    <>
      <button className="back-link" onClick={() => navigate("/")}>
        <ArrowLeft size={17} /> 저장된 시설 목록으로
      </button>
      <div className="page-intro intro-programs">
        <div>
          <span className="eyebrow">PUBLIC DATA API</span>
          <h1>
            전국 체육시설을
            <br />
            <em>실시간으로 찾아요.</em>
          </h1>
          <p>
            공공데이터포털 체육시설 API의 조회 결과입니다.
            <br />
            운영상태와 방문 가능 여부는 해당 시설에 확인해 주세요.
          </p>
        </div>
      </div>
      <section className="page-section">
        <SectionTitle
          eyebrow="LIVE FACILITIES"
          title="공공 API 시설 검색"
          subtitle="시·도 → 시·군·구 → 읍·면·동 순으로 지역을 좁혀 검색하세요."
          aside={
            <span className="result-pill">
              총 {total.toLocaleString("ko-KR")}개
            </span>
          }
        />
        <form
          className="filter-bar program-filter live-filter"
          onSubmit={submit}
        >
          <label className="select-wrap">
            <MapPin size={17} />
            <select
              value={province}
              aria-label="시도 선택"
              onChange={(event) => {
                setProvince(event.target.value);
                setCity("");
                setSubmittedCity("");
                setLocality("");
                setPage(0);
              }}
            >
              <option value="">전국 시·도</option>
              {provinces.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
            <ChevronDown size={15} />
          </label>
          <div className="search-field">
            <input
              value={city}
              disabled={!province}
              onChange={(event) => setCity(event.target.value)}
              placeholder="시·군·구 (예: 강남구)"
              aria-label="시군구"
            />
          </div>
          <label className="select-wrap">
            <select
              value={locality}
              disabled={!submittedCity || localities.loading}
              aria-label="읍면동 선택"
              onChange={(event) => {
                setLocality(event.target.value);
                setPage(0);
              }}
            >
              <option value="">
                {localities.loading ? "읍·면·동 불러오는 중" : "전체 읍·면·동"}
              </option>
              {(Array.isArray(localities.data) ? localities.data : []).map(
                (name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ),
              )}
            </select>
            <ChevronDown size={15} />
          </label>
          <div className="search-field">
            <Search size={19} />
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="시설명 (선택)"
              aria-label="시설명"
            />
            <button type="submit">검색</button>
          </div>
        </form>
        {localities.error && (
          <p className="form-error">
            하위지역을 불러오지 못했습니다: {localities.error}
          </p>
        )}
        <p className="muted">
          시·군·구 이름을 입력하고 검색하면 읍·면·동 선택지가 나타납니다.
          운영상태가 폐업인 시설도 원천 자료에 포함될 수 있습니다.
        </p>
        <div className="explore-layout">
          <div className="facility-results">
            <div className="results-header">
              <strong>
                검색 결과 <em>{total.toLocaleString("ko-KR")}</em>
              </strong>
              <span>공공데이터포털 제공 자료</span>
            </div>
            {result.loading ? (
              <LoadingCards />
            ) : result.error ? (
              <PageError message={result.error} retry={result.reload} />
            ) : items.length ? (
              <>
                <div className="facility-grid">
                  {items.map((item) => (
                    <FacilityCard key={item.id} item={item} onOpen={open} />
                  ))}
                </div>
                <Pagination
                  page={{ number: page, totalPages: pages }}
                  onPage={setPage}
                />
              </>
            ) : (
              <Empty
                icon={Search}
                title="검색 결과가 없어요"
                description="다른 시설명이나 지역으로 다시 찾아보세요."
              />
            )}
          </div>
          <FacilityMap items={items} onOpen={open} />
        </div>
      </section>
      {selected && (
        <ExternalFacilityModal
          item={selected}
          close={() => setSelected(null)}
        />
      )}
    </>
  );
}

// 시설 상세 정보와 등록 프로그램·후기·위치를 보여준다.
function FacilityDetailPage({ id, navigate, notify, requireAuth }) {
  const facility = useRemote(`/api/facilities/${id}`);
  const programs = useRemote(
    query("/api/programs", { facilityId: id, size: 30 }),
  );
  const reviews = useRemote(`/api/facilities/${id}/reviews?size=20`);
  const [rating, setRating] = useState(5);
  const [reviewText, setReviewText] = useState("");
  const [saving, setSaving] = useState(false);
  async function postReview(event) {
    event.preventDefault();
    if (!requireAuth()) return;
    setSaving(true);
    try {
      await api(`/api/facilities/${id}/reviews`, {
        method: "POST",
        body: json({ rating, content: reviewText }),
      });
      setReviewText("");
      reviews.reload();
      notify("리뷰를 등록했습니다.");
    } catch (error) {
      notify(error.message, "error");
    } finally {
      setSaving(false);
    }
  }
  if (facility.loading) return <LoadingCards count={2} />;
  if (facility.error)
    return <PageError message={facility.error} retry={facility.reload} />;
  const item = facility.data;
  const Icon = iconByType(item.type);
  return (
    <>
      <button className="back-link" onClick={() => navigate("/")}>
        <ArrowLeft size={17} /> 시설 목록으로
      </button>
      <div className={`detail-hero tone-${item.id % 4}`}>
        <div className="detail-hero-pattern" />
        <span className="detail-icon">
          <Icon size={38} />
        </span>
        <span className="detail-hero-label">
          {item.publicFacility ? "PUBLIC SPORTS FACILITY" : "SPORTS FACILITY"}
        </span>
      </div>
      <div className="detail-header">
        <div>
          <span className="eyebrow">FACILITY DETAIL</span>
          <h1>{item.name}</h1>
          <p>
            <MapPin size={17} /> {item.roadAddress || "주소 정보 없음"}
          </p>
        </div>
        <span className="detail-badge">{item.type || "체육시설"}</span>
      </div>
      <div className="detail-columns">
        <div>
          <div className="panel">
            <SectionTitle eyebrow="ABOUT THIS PLACE" title="시설 안내" />
            <div className="info-list">
              <div>
                <span>시설 유형</span>
                <strong>{item.type || "정보 없음"}</strong>
              </div>
              <div>
                <span>지역</span>
                <strong>{item.regionName || "정보 없음"}</strong>
              </div>
              <div>
                <span>문의 전화</span>
                <strong>
                  {item.phone ? formatPhone(item.phone) : "정보 없음"}
                </strong>
              </div>
              <div>
                <span>예약 가능</span>
                <strong>{item.reservable ? "가능" : "기관 확인 필요"}</strong>
              </div>
            </div>
            {item.website && (
              <a
                className="text-link"
                href={item.website}
                target="_blank"
                rel="noreferrer"
              >
                운영기관 홈페이지 <ArrowUpRight size={16} />
              </a>
            )}
          </div>
          <div className="panel">
            <SectionTitle
              eyebrow="PROGRAMS"
              title="운영 프로그램"
              aside={
                <button
                  className="text-link"
                  onClick={() => navigate("/programs")}
                >
                  전체 보기 <ArrowRight size={16} />
                </button>
              }
            />
            {programs.loading ? (
              <LoadingCards count={1} />
            ) : pageList(programs.data).length ? (
              <div className="compact-program-list">
                {pageList(programs.data).map((program) => (
                  <button
                    key={program.id}
                    onClick={() => navigate(`/programs/${program.id}`)}
                  >
                    <span className="compact-program-icon">
                      <Dumbbell size={19} />
                    </span>
                    <span>
                      <strong>{program.name}</strong>
                      <small>
                        {program.scheduleText ||
                          program.sportType ||
                          "일정 확인 필요"}
                      </small>
                    </span>
                    <ArrowRight size={17} />
                  </button>
                ))}
              </div>
            ) : (
              <Empty
                icon={Dumbbell}
                title="등록된 프로그램이 없어요"
                description="운영기관의 최신 일정을 확인해 주세요."
              />
            )}
          </div>
        </div>
        <div>
          <div className="panel">
            <SectionTitle eyebrow="LOCATION" title="위치 보기" />
            <div className="detail-map">
              {item.latitude != null && item.longitude != null ? (
                <MapContainer
                  center={[Number(item.latitude), Number(item.longitude)]}
                  zoom={15}
                  scrollWheelZoom={false}
                  className="leaflet-map"
                >
                  <TileLayer
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                    url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                  />
                  <Marker
                    position={[Number(item.latitude), Number(item.longitude)]}
                    icon={markerIcon}
                  />
                </MapContainer>
              ) : (
                <Empty
                  icon={MapPin}
                  title="위치 정보가 없어요"
                  description="주소를 참고해 주세요."
                />
              )}
            </div>
            <p className="map-address">
              <MapPin size={15} />
              {item.roadAddress || "주소 정보 없음"}
            </p>
          </div>
          <div className="panel">
            <SectionTitle
              eyebrow="REVIEWS"
              title={`이용 후기 ${reviews.data?.page?.totalElements ?? 0}`}
            />
            {pageList(reviews.data).map((review) => (
              <div className="review-item" key={review.id}>
                <span className="review-stars">
                  {"★".repeat(review.rating)}
                  {"☆".repeat(5 - review.rating)}
                </span>
                <p>{review.content}</p>
                <small>{shortDate(review.createdAt)}</small>
              </div>
            ))}
            {!reviews.loading && !pageList(reviews.data).length && (
              <p className="muted">첫 이용 후기를 남겨주세요.</p>
            )}
            <form className="review-form" onSubmit={postReview}>
              <div className="review-form-top">
                <strong>후기 남기기</strong>
                <select
                  value={rating}
                  onChange={(event) => setRating(Number(event.target.value))}
                  aria-label="평점"
                >
                  {[5, 4, 3, 2, 1].map((value) => (
                    <option key={value} value={value}>
                      {"★".repeat(value)} {value}점
                    </option>
                  ))}
                </select>
              </div>
              <textarea
                value={reviewText}
                onChange={(event) => setReviewText(event.target.value)}
                placeholder="시설을 이용한 경험을 나눠주세요."
                required
                maxLength={2000}
              />
              <button
                className="button button-dark"
                disabled={saving || !reviewText.trim()}
              >
                후기 등록 <ArrowRight size={16} />
              </button>
            </form>
          </div>
        </div>
      </div>
    </>
  );
}

// 프로그램의 기간·대상·요금을 카드에 요약한다.
function ProgramCard({ item, navigate, onApply }) {
  const Icon = iconByType(item.sportType);
  return (
    <article className="program-card">
      <div className="program-top">
        <span className={`program-icon tone-${item.id % 4}`}>
          <Icon size={26} />
        </span>
        <span
          className={`program-badge ${item.bookingSupported ? "open" : ""}`}
        >
          {item.bookingSupported ? "바로 예약" : "신청 접수"}
        </span>
      </div>
      <div className="program-category">{item.sportType || "생활체육"}</div>
      <h3>{item.name}</h3>
      <div className="program-location">
        <span>
          <Users size={15} />{" "}
          {item.operatingOrganization || "운영기관 정보 없음"}
        </span>
        <span>
          <MapPin size={15} /> {item.regionName || "지역 정보 없음"}
        </span>
      </div>
      <p className="program-subline">
        <Clock3 size={15} />{" "}
        {item.scheduleText || "일정은 운영기관에 문의해 주세요"}
      </p>
      <div className="program-facts">
        <div>
          <span>대상</span>
          <strong>{item.eligibility || "누구나"}</strong>
        </div>
        <div>
          <span>운영 기간</span>
          <strong>
            {item.beginsOn
              ? `${shortDate(item.beginsOn)} – ${shortDate(item.endsOn)}`
              : "시간 선택형"}
          </strong>
        </div>
        <div>
          <span>모집 정원</span>
          <strong>
            {item.capacity == null ? "문의" : `${item.capacity}명`}
          </strong>
        </div>
      </div>
      <div className="program-bottom">
        <div>
          <small>이용 요금</small>
          <strong>{currency(item.fee)}</strong>
        </div>
        <div className="program-actions">
          <button
            className="outline-icon"
            title="자세히 보기"
            onClick={() => navigate(`/programs/${item.id}`)}
          >
            <ArrowUpRight size={18} />
          </button>
          <button className="button button-dark" onClick={() => onApply(item)}>
            {item.bookingSupported ? "예약하기" : "신청 기록"}{" "}
            <ArrowRight size={16} />
          </button>
        </div>
      </div>
    </article>
  );
}

// 적재된 공공 프로그램을 검색하고 페이지별로 보여준다.
function ProgramsPage({ navigate, onApply }) {
  const [keyword, setKeyword] = useState("");
  const [submitted, setSubmitted] = useState("");
  const [province, setProvince] = useState("");
  const [city, setCity] = useState("");
  const [locality, setLocality] = useState("");
  const [submittedRegion, setSubmittedRegion] = useState("");
  const [page, setPage] = useState(0);
  const regionOptions = useRemote("/api/programs/regions");
  // 프로그램이 등록된 시설 주소만 사용해 각 단계의 중복 없는 선택지를 만든다.
  const provinces = useMemo(
    () =>
      [
        ...new Set((regionOptions.data || []).map((option) => option.province)),
      ].filter(Boolean),
    [regionOptions.data],
  );
  const cities = useMemo(
    () =>
      [
        ...new Set(
          (regionOptions.data || [])
            .filter((option) => option.province === province)
            .map((option) => option.city),
        ),
      ].filter(Boolean),
    [regionOptions.data, province],
  );
  const localities = useMemo(
    () =>
      [
        ...new Set(
          (regionOptions.data || [])
            .filter(
              (option) => option.province === province && option.city === city,
            )
            .map((option) => option.locality),
        ),
      ].filter(Boolean),
    [regionOptions.data, province, city],
  );
  const programs = useRemote(
    query("/api/programs", {
      keyword: submitted,
      region: submittedRegion,
      page,
      size: 9,
    }),
  );
  const submit = (event) => {
    event.preventDefault();
    setPage(0);
    setSubmitted(keyword.trim());
    setSubmittedRegion([province, city, locality].filter(Boolean).join(" "));
  };
  return (
    <>
      <div className="page-intro intro-programs">
        <div>
          <span className="eyebrow">FIND YOUR PROGRAM</span>
          <h1>
            나의 일상에 맞는
            <br />
            <em>운동 시간을 찾아요.</em>
          </h1>
          <p>
            가까운 시설의 다양한 체육 프로그램을 확인하고
            <br />
            새로운 움직임을 시작해 보세요.
          </p>
        </div>
        <div className="intro-illustration">
          <div className="intro-circle">
            <Dumbbell size={85} strokeWidth={1.1} />
          </div>
          <span className="intro-small one">
            <Waves size={25} />
          </span>
          <span className="intro-small two">
            <Footprints size={25} />
          </span>
        </div>
      </div>
      <section className="page-section">
        <SectionTitle
          eyebrow="ALL PROGRAMS"
          title="프로그램 둘러보기"
          subtitle="가격과 운영 기간을 확인하고, 나에게 맞는 프로그램을 선택하세요."
          aside={
            <span className="result-pill">
              총 {programs.data?.page?.totalElements ?? 0}개
            </span>
          }
        />
        <form className="filter-bar program-filter" onSubmit={submit}>
          <div className="program-region-fields">
            <div className="program-region-field">
              <MapPin size={19} />
              <select
                value={province}
                onChange={(event) => {
                  setProvince(event.target.value);
                  setCity("");
                  setLocality("");
                }}
                aria-label="프로그램 시도 검색"
              >
                <option value="">전체 시·도</option>
                {provinces.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </div>
            <div className="program-region-field">
              <select
                value={city}
                onChange={(event) => {
                  setCity(event.target.value);
                  setLocality("");
                }}
                disabled={!province}
                aria-label="프로그램 시군구 검색"
              >
                <option value="">전체 시·군·구</option>
                {cities.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </div>
            <div className="program-region-field">
              <select
                value={locality}
                onChange={(event) => setLocality(event.target.value)}
                disabled={!city || localities.length === 0}
                aria-label="프로그램 하위 구역 검색"
              >
                <option value="">전체 하위 구역</option>
                {localities.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="search-field">
            <Search size={19} />
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="프로그램명 검색"
              aria-label="프로그램 검색"
            />
            <button type="submit">검색</button>
          </div>
        </form>
        {programs.loading ? (
          <LoadingCards />
        ) : programs.error ? (
          <PageError message={programs.error} retry={programs.reload} />
        ) : pageList(programs.data).length ? (
          <>
            <div className="program-grid">
              {pageList(programs.data).map((item) => (
                <ProgramCard
                  key={item.id}
                  item={item}
                  navigate={navigate}
                  onApply={onApply}
                />
              ))}
            </div>
            <Pagination page={programs.data.page} onPage={setPage} />
          </>
        ) : (
          <Empty
            icon={Dumbbell}
            title="프로그램이 없어요"
            description="다른 검색어를 입력하거나 공공 프로그램 데이터를 적재해 주세요."
          />
        )}
      </section>
    </>
  );
}

// 프로그램 일정과 시설 정보를 확인하고 신청으로 연결한다.
function ProgramDetailPage({ id, navigate, onApply }) {
  const program = useRemote(`/api/programs/${id}`);
  const facilityId = program.data?.facilityId;
  const facility = useRemote(
    facilityId ? `/api/facilities/${facilityId}` : "/api/regions",
  );
  if (program.loading) return <LoadingCards count={2} />;
  if (program.error)
    return <PageError message={program.error} retry={program.reload} />;
  const item = program.data;
  const Icon = iconByType(item.sportType);
  return (
    <>
      <button className="back-link" onClick={() => navigate("/programs")}>
        <ArrowLeft size={17} /> 프로그램 목록으로
      </button>
      <div className="program-detail-layout">
        <div>
          <div className="program-detail-banner">
            <span className="eyebrow">PROGRAM DETAIL</span>
            <span className="program-detail-art">
              <Icon size={105} strokeWidth={1.2} />
            </span>
            <h1>{item.name}</h1>
            <p>
              {item.sportType || "체육 프로그램"} ·{" "}
              {item.scheduleText || "일정 확인 필요"}
            </p>
          </div>
          <div className="panel">
            <SectionTitle eyebrow="AT A GLANCE" title="프로그램 안내" />
            <div className="info-list">
              <div>
                <span>운동 종목</span>
                <strong>{item.sportType || "정보 없음"}</strong>
              </div>
              <div>
                <span>운영 기관</span>
                <strong>{item.operatingOrganization || "정보 없음"}</strong>
              </div>
              <div>
                <span>지역</span>
                <strong>{item.regionName || "정보 없음"}</strong>
              </div>
              <div>
                <span>대상</span>
                <strong>{item.eligibility || "누구나"}</strong>
              </div>
              <div>
                <span>운영 일정</span>
                <strong>{item.scheduleText || "기관 문의"}</strong>
              </div>
              <div>
                <span>운영 기간</span>
                <strong>
                  {item.beginsOn
                    ? `${date(item.beginsOn)} ~ ${date(item.endsOn)}`
                    : "예약 시간 선택형"}
                </strong>
              </div>
              <div>
                <span>모집 정원</span>
                <strong>
                  {item.capacity == null ? "기관 문의" : `${item.capacity}명`}
                </strong>
              </div>
              <div>
                <span>이용 요금</span>
                <strong>{currency(item.fee)}</strong>
              </div>
            </div>
          </div>
        </div>
        <div className="program-side">
          <div className="panel booking-panel">
            <span
              className={`program-badge ${item.bookingSupported ? "open" : ""}`}
            >
              {item.bookingSupported ? "자체 예약 가능" : "공공 프로그램"}
            </span>
            <h3>
              나에게 맞는
              <br />
              운동을 시작해 보세요.
            </h3>
            <p>
              {item.bookingSupported
                ? "원하는 날짜와 시간을 골라 예약하세요."
                : "신청 내역을 기록할 수 있습니다. 운영기관 접수·확정과는 별개입니다."}
            </p>
            <div className="booking-price">
              <span>이용 요금</span>
              <strong>{currency(item.fee)}</strong>
            </div>
            <button
              className="button button-lime wide"
              onClick={() => onApply(item)}
            >
              {item.bookingSupported ? "예약하기" : "신청 기록하기"}{" "}
              <ArrowRight size={17} />
            </button>
            {item.registrationUrl && (
              <a
                className="text-link centered"
                href={item.registrationUrl}
                target="_blank"
                rel="noreferrer"
              >
                운영기관 홈페이지 <ArrowUpRight size={16} />
              </a>
            )}
          </div>
          {facility.data && facilityId && (
            <div className="panel facility-mini">
              <span className="eyebrow">WHERE TO GO</span>
              <h3>{facility.data.name}</h3>
              <p>
                <MapPin size={15} />{" "}
                {facility.data.roadAddress || "주소 정보 없음"}
              </p>
              <button
                className="text-link"
                onClick={() => navigate(`/facilities/${facilityId}`)}
              >
                시설 자세히 보기 <ArrowRight size={16} />
              </button>
            </div>
          )}
        </div>
      </div>
    </>
  );
}

// 입력한 연령에 맞는 추천 운동을 조회한다.
function RecommendationsPage() {
  const [age, setAge] = useState(30);
  const [bmi, setBmi] = useState("정상");
  const [sex, setSex] = useState("F");
  const [grade, setGrade] = useState("참가증");
  const [filters, setFilters] = useState({
    age: 30,
    bmi: "정상",
    sex: "F",
    grade: "참가증",
  });
  const recommendations = useRemote(
    `/api/recommendations?${new URLSearchParams(filters)}`,
  );
  const [catalogSource, setCatalogSource] = useState("GENERAL");
  const [catalogSex, setCatalogSex] = useState("F");
  const [catalogGrade, setCatalogGrade] = useState("");
  const [disabilityType, setDisabilityType] = useState("지적장애");
  const prescriptions = useRemote(
    `/api/fitness/prescriptions?${new URLSearchParams({ age: filters.age, source: catalogSource, sex: catalogSex, grade: catalogSource === "DISABILITY" ? "" : catalogGrade, disabilityType: catalogSource === "DISABILITY" ? disabilityType : "" })}`,
  );
  const submit = (event) => {
    event.preventDefault();
    setFilters({
      age: Math.max(0, Math.min(120, Number(age))),
      bmi,
      sex,
      grade,
    });
  };
  return (
    <>
      <div className="page-intro intro-recommend">
        <div>
          <span className="eyebrow">MOVE FOR YOU</span>
          <h1>
            내 몸에 맞는 움직임,
            <br />
            <em>여기서 시작해요.</em>
          </h1>
          <p>
            연령대와 몸 상태에 맞는 추천 운동을 찾아보세요.
            <br />
            꾸준한 움직임이 건강한 일상을 만듭니다.
          </p>
        </div>
        <div className="recommend-art">
          <div>
            <HeartPulse size={72} strokeWidth={1.3} />
          </div>
          <span>
            <Sparkles size={28} />
          </span>
        </div>
      </div>
      <section className="page-section">
        <SectionTitle
          eyebrow="PERSONALIZED PICKS"
          title="나에게 추천하는 운동"
          subtitle="연령·BMI 분류·성별·체력 등급에 맞는 준비, 본, 마무리 운동을 보여드려요."
        />
        <form className="age-form" onSubmit={submit}>
          <label>
            나의 나이{" "}
            <div>
              <input
                type="number"
                min="0"
                max="120"
                value={age}
                onChange={(event) => setAge(event.target.value)}
                aria-label="나이"
              />
              <span>세</span>
            </div>
          </label>
          <label>
            BMI 분류
            <select
              value={bmi}
              onChange={(event) => setBmi(event.target.value)}
            >
              {[
                "저체중",
                "정상",
                "비만전단계비만",
                "1단계비만",
                "2단계비만",
                "3단계비만",
              ].map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </label>
          <label>
            성별
            <select
              value={sex}
              onChange={(event) => setSex(event.target.value)}
            >
              <option value="F">여성</option>
              <option value="M">남성</option>
            </select>
          </label>
          <label>
            체력 등급
            <select
              value={grade}
              onChange={(event) => setGrade(event.target.value)}
            >
              {["1등급", "2등급", "3등급", "참가증"].map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </label>
          <button className="button button-dark">
            추천 보기 <ArrowRight size={17} />
          </button>
        </form>
        {recommendations.loading ? (
          <LoadingCards count={2} />
        ) : recommendations.error ? (
          <PageError
            message={recommendations.error}
            retry={recommendations.reload}
          />
        ) : recommendations.data?.length ? (
          <div className="recommend-grid">
            {recommendations.data.map((item, index) => {
              const Icon = iconByType(item.exerciseName);
              return (
                <article
                  className={`recommend-card tone-${index % 4}`}
                  key={item.id}
                >
                  <div className="recommend-card-top">
                    <span className="recommend-number">0{index + 1}</span>
                    <Icon size={44} strokeWidth={1.3} />
                  </div>
                  <span className="eyebrow">
                    {item.exerciseStep} · {item.recommendationRank}순위
                  </span>
                  <h3>{item.exerciseName}</h3>
                  <p>국민체육100 연령별 운동정보 추천</p>
                  <div className="recommend-age">
                    <Users size={16} /> {item.minAge}~{item.maxAge}세 ·{" "}
                    {item.bmiCategory} · {item.fitnessGrade}
                  </div>
                </article>
              );
            })}
          </div>
        ) : (
          <Empty
            icon={HeartPulse}
            title="추천 운동이 아직 없어요"
            description="다른 나이를 입력하거나 추천 운동 데이터를 적재해 주세요."
          />
        )}
      </section>
      <section className="page-section">
        <SectionTitle
          eyebrow="FITNESS PRESCRIPTIONS"
          title="체력측정 운동처방 사례"
          subtitle="공공데이터의 개인 기록을 익명 집계한 참고 사례입니다. 실제 운동은 자신의 상태에 맞춰 조절하세요."
        />
        <div className="prescription-controls">
          <label>
            데이터 종류
            <select
              value={catalogSource}
              onChange={(event) => setCatalogSource(event.target.value)}
            >
              <option value="GENERAL">일반 체력측정</option>
              <option value="LOCAL">지역 체력측정</option>
              <option value="DISABILITY">장애인 체력측정</option>
            </select>
          </label>
          <label>
            성별
            <select
              value={catalogSex}
              onChange={(event) => setCatalogSex(event.target.value)}
            >
              <option value="F">여성</option>
              <option value="M">남성</option>
            </select>
          </label>
          {catalogSource === "DISABILITY" ? (
            <label>
              장애 유형
              <select
                value={disabilityType}
                onChange={(event) => setDisabilityType(event.target.value)}
              >
                {[
                  "지체장애",
                  "뇌병변장애",
                  "지적장애",
                  "자폐성장애",
                  "정신장애",
                  "청각장애",
                ].map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <label>
              원본 공식 등급
              <select
                value={catalogGrade}
                onChange={(event) => setCatalogGrade(event.target.value)}
              >
                <option value="">전체 등급</option>
                {[
                  "1등급",
                  "2등급",
                  "3등급",
                  "4등급",
                  "5등급",
                  "6등급",
                  "참가",
                ].map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </label>
          )}
        </div>
        {prescriptions.loading ? (
          <LoadingCards count={2} />
        ) : prescriptions.error ? (
          <PageError
            message={prescriptions.error}
            retry={prescriptions.reload}
          />
        ) : prescriptions.data?.length ? (
          <div className="prescription-grid">
            {prescriptions.data.map((item, index) => (
              <article
                className="prescription-card"
                key={`${item.source}-${item.ageBand}-${item.grade}-${item.disabilityType}-${index}`}
              >
                <div className="prescription-meta">
                  {item.ageBand} ·{" "}
                  {item.grade || item.disabilityType || "처방 사례"} ·{" "}
                  {item.count}건
                </div>
                {[
                  ["준비운동", item.warmup],
                  ["본운동", item.main],
                  ["마무리운동", item.cooldown],
                ].map(([title, movements]) =>
                  movements?.length ? (
                    <div key={title}>
                      <strong>{title}</strong>
                      <p>{movements.join(" · ")}</p>
                    </div>
                  ) : null,
                )}
              </article>
            ))}
          </div>
        ) : (
          <Empty
            icon={HeartPulse}
            title="해당 조건의 처방 사례가 없어요"
            description="다른 연령이나 데이터 종류를 선택해 주세요."
          />
        )}
      </section>
    </>
  );
}

// 공개된 체력측정센터의 위치와 운영 연락처를 검색한다.
function FitnessCentersPage() {
  const [keyword, setKeyword] = useState("");
  const [submitted, setSubmitted] = useState("");
  const centers = useRemote(
    `/api/fitness/centers?${new URLSearchParams({ keyword: submitted })}`,
  );
  return (
    <>
      <div className="page-intro intro-recommend">
        <div>
          <span className="eyebrow">FITNESS CENTERS</span>
          <h1>체력측정센터 찾기</h1>
          <p>공공데이터에 수록된 센터 연락처와 운영시간을 확인하세요.</p>
        </div>
      </div>
      <section className="page-section">
        <form
          className="center-search"
          onSubmit={(event) => {
            event.preventDefault();
            setSubmitted(keyword);
          }}
        >
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="지역, 센터 이름 또는 전화번호"
            aria-label="센터 검색"
          />
          <button className="button button-dark">검색</button>
        </form>
        {centers.loading ? (
          <LoadingCards count={2} />
        ) : centers.error ? (
          <PageError message={centers.error} retry={centers.reload} />
        ) : centers.data?.length ? (
          <div className="center-grid">
            {centers.data.map((center, index) => (
              <article
                className="center-card"
                key={`${center.province}-${center.name}-${index}`}
              >
                <h3>{center.name} 체력인증센터</h3>
                <p>
                  <MapPin size={16} /> {center.province} {center.district}{" "}
                  {center.road} {center.buildingNumber}
                </p>
                {center.operatingDays && (
                  <p>
                    <Clock3 size={16} /> {center.operatingDays}{" "}
                    {center.operatingHours}
                  </p>
                )}
                {center.phone && (
                  <a
                    className="center-phone"
                    href={`tel:${center.phone}`}
                    aria-label={`${center.name} 센터에 전화하기`}
                  >
                    전화 {formatPhone(center.phone)}
                  </a>
                )}
              </article>
            ))}
          </div>
        ) : (
          <Empty
            icon={MapPin}
            title="검색된 센터가 없어요"
            description="다른 지역명을 입력해 주세요."
          />
        )}
      </section>
    </>
  );
}

// 회원의 예약·신청 내역을 조회하고 취소를 처리한다.
function ReservationsPage({ user, openAuth, notify }) {
  const [refresh, setRefresh] = useState(0);
  const reservations = useRemote(
    user ? `/api/reservations?size=30&r=${refresh}` : "/api/csrf",
  );
  const [programMap, setProgramMap] = useState({});
  useEffect(() => {
    if (!user || !reservations.data?.content) return;
    const ids = [
      ...new Set(reservations.data.content.map((item) => item.programId)),
    ];
    Promise.all(
      ids.map((id) =>
        api(`/api/programs/${id}`)
          .then((program) => [id, program])
          .catch(() => [id, null]),
      ),
    ).then((entries) => setProgramMap(Object.fromEntries(entries)));
  }, [user, reservations.data]);
  async function cancel(id) {
    if (!window.confirm("이 예약 또는 신청 내역을 취소할까요?")) return;
    try {
      await api(`/api/reservations/${id}/cancel`, { method: "PATCH" });
      notify("취소했습니다.");
      setRefresh((value) => value + 1);
    } catch (error) {
      notify(error.message, "error");
    }
  }
  return (
    <>
      <div className="page-intro intro-reservations">
        <div>
          <span className="eyebrow">MY ACTIVITY</span>
          <h1>
            나의 운동 일정,
            <br />
            <em>한눈에 관리해요.</em>
          </h1>
          <p>
            신청한 프로그램과 예약 내역을 확인하고
            <br />
            필요할 때 간편하게 취소할 수 있어요.
          </p>
        </div>
        <div className="reservation-art">
          <CalendarDays size={94} strokeWidth={1.1} />
          <span>
            <Check size={22} />
          </span>
        </div>
      </div>
      <section className="page-section">
        <SectionTitle
          eyebrow="MY BOOKINGS"
          title="예약·신청 내역"
          subtitle="공공 프로그램의 ‘신청 접수’는 운영기관 예약 확정과 다릅니다."
        />
        {!user ? (
          <Empty
            icon={LogIn}
            title="로그인이 필요해요"
            description="로그인하면 내 예약과 신청 내역을 볼 수 있습니다."
            action={
              <button className="button button-dark" onClick={openAuth}>
                로그인하기 <ArrowRight size={17} />
              </button>
            }
          />
        ) : reservations.loading ? (
          <LoadingCards count={2} />
        ) : reservations.error ? (
          <PageError message={reservations.error} retry={reservations.reload} />
        ) : pageList(reservations.data).length ? (
          <div className="reservation-list">
            {pageList(reservations.data).map((item) => (
              <article className="reservation-card" key={item.id}>
                <span
                  className={`reservation-status ${item.status.toLowerCase()}`}
                >
                  <span />
                  {statusText[item.status] || item.status}
                </span>
                <div className="reservation-main">
                  <span className="reservation-icon">
                    <CalendarDays size={25} />
                  </span>
                  <div>
                    <small>PROGRAM #{item.programId}</small>
                    <h3>
                      {programMap[item.programId]?.name ||
                        `프로그램 ${item.programId}`}
                    </h3>
                    <p>
                      <Clock3 size={15} /> {date(item.startsAt)} –{" "}
                      {date(item.endsAt)}
                    </p>
                  </div>
                </div>
                <div className="reservation-bottom">
                  <span>
                    {item.status === "REQUESTED"
                      ? "운영기관 접수 상태는 별도로 확인해 주세요."
                      : item.status === "CONFIRMED"
                        ? "예약이 확정되었습니다."
                        : "취소된 내역입니다."}
                  </span>
                  {item.status !== "CANCELLED" && (
                    <button onClick={() => cancel(item.id)}>
                      취소하기 <ArrowRight size={15} />
                    </button>
                  )}
                </div>
              </article>
            ))}
          </div>
        ) : (
          <Empty
            icon={Ticket}
            title="아직 예약 내역이 없어요"
            description="마음에 드는 프로그램을 찾아 첫 운동을 시작해 보세요."
          />
        )}
      </section>
    </>
  );
}

// 공지와 질문 목록을 전환하고 새 질문을 등록한다.
function CommunityPage({ navigate, user, openAuth, notify }) {
  const [tab, setTab] = useState("notices");
  const [page, setPage] = useState(0);
  const [compose, setCompose] = useState(false);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [refresh, setRefresh] = useState(0);
  const posts = useRemote(`/api/${tab}?page=${page}&size=10&r=${refresh}`);
  async function create(event) {
    event.preventDefault();
    if (!user) return openAuth();
    try {
      const created = await api("/api/qna", {
        method: "POST",
        body: json({ title, content }),
      });
      setCompose(false);
      setTitle("");
      setContent("");
      setRefresh((value) => value + 1);
      notify("질문을 등록했습니다.");
      navigate(`/community/qna/${created.id}`);
    } catch (error) {
      notify(error.message, "error");
    }
  }
  return (
    <>
      <div className="page-intro intro-community">
        <div>
          <span className="eyebrow">TOGETHER WE MOVE</span>
          <h1>
            함께 나누는
            <br />
            <em>운동 이야기.</em>
          </h1>
          <p>
            새로운 소식도 확인하고, 궁금한 점도 물어보세요.
            <br />더 즐거운 운동 생활을 함께 만들어가요.
          </p>
        </div>
        <div className="community-art">
          <MessageCircle size={100} strokeWidth={1.1} />
          <span>
            <Sparkles size={28} />
          </span>
        </div>
      </div>
      <section className="page-section">
        <SectionTitle
          eyebrow="COMMUNITY"
          title="소식과 이야기"
          subtitle="시설 이용 정보와 운동 생활의 궁금증을 나눠보세요."
          aside={
            tab === "qna" && (
              <button
                className="button button-dark"
                onClick={() => (user ? setCompose(true) : openAuth())}
              >
                <Send size={16} /> 질문하기
              </button>
            )
          }
        />
        <div className="tabs">
          <button
            className={tab === "notices" ? "active" : ""}
            onClick={() => {
              setTab("notices");
              setPage(0);
            }}
          >
            <ShieldCheck size={17} /> 공지사항
          </button>
          <button
            className={tab === "qna" ? "active" : ""}
            onClick={() => {
              setTab("qna");
              setPage(0);
            }}
          >
            <MessageCircle size={17} /> 질문과 답변
          </button>
        </div>
        {posts.loading ? (
          <LoadingCards count={2} />
        ) : posts.error ? (
          <PageError message={posts.error} retry={posts.reload} />
        ) : pageList(posts.data).length ? (
          <>
            <div className="post-list">
              {pageList(posts.data).map((post, index) => (
                <button
                  key={post.id}
                  onClick={() => navigate(`/community/${tab}/${post.id}`)}
                >
                  <span className={`post-icon ${tab}`}>
                    <MessageCircle size={20} />
                  </span>
                  <span className="post-text">
                    <small>
                      {tab === "notices" ? "공지사항" : "질문과 답변"} ·{" "}
                      {shortDate(post.createdAt)}
                    </small>
                    <strong>{post.title}</strong>
                    <span>{post.content}</span>
                  </span>
                  <ArrowUpRight size={18} />
                </button>
              ))}
            </div>
            <Pagination page={posts.data.page} onPage={setPage} />
          </>
        ) : (
          <Empty
            icon={MessageCircle}
            title="아직 등록된 글이 없어요"
            description={
              tab === "qna"
                ? "첫 질문을 남겨보세요."
                : "새로운 공지가 등록되면 이곳에서 볼 수 있어요."
            }
          />
        )}
      </section>
      {compose && (
        <div className="modal-backdrop" onMouseDown={() => setCompose(false)}>
          <div
            className="modal dialog-modal"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button className="modal-close" onClick={() => setCompose(false)}>
              <X size={20} />
            </button>
            <span className="eyebrow">NEW QUESTION</span>
            <h2>새 질문 작성</h2>
            <p>궁금한 점을 자유롭게 남겨주세요.</p>
            <form onSubmit={create}>
              <label>
                제목
                <input
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  required
                  maxLength={200}
                  placeholder="질문 제목을 입력하세요"
                />
              </label>
              <label>
                내용
                <textarea
                  value={content}
                  onChange={(event) => setContent(event.target.value)}
                  required
                  maxLength={5000}
                  placeholder="내용을 자세히 적어주세요"
                />
              </label>
              <button className="button button-dark wide">
                질문 등록 <ArrowRight size={17} />
              </button>
            </form>
          </div>
        </div>
      )}
    </>
  );
}

// 글 상세와 댓글을 조회하고 답글을 등록한다.
function CommunityDetailPage({ kind, id, navigate, user, openAuth, notify }) {
  const post = useRemote(`/api/${kind}/${id}`);
  const comments = useRemote(
    kind === "qna" ? `/api/qna/${id}/comments` : "/api/csrf",
  );
  const [content, setContent] = useState("");
  const [replyTo, setReplyTo] = useState(null);
  async function send(event) {
    event.preventDefault();
    if (!user) return openAuth();
    try {
      await api(`/api/qna/${id}/comments`, {
        method: "POST",
        body: json({ parentId: replyTo, content }),
      });
      setContent("");
      setReplyTo(null);
      comments.reload();
      notify("댓글을 등록했습니다.");
    } catch (error) {
      notify(error.message, "error");
    }
  }
  if (post.loading) return <LoadingCards count={1} />;
  if (post.error) return <PageError message={post.error} retry={post.reload} />;
  return (
    <>
      <button className="back-link" onClick={() => navigate("/community")}>
        <ArrowLeft size={17} /> 커뮤니티로
      </button>
      <div className="post-detail panel">
        <span className="eyebrow">
          {kind === "qna" ? "QUESTION & ANSWER" : "NOTICE"}
        </span>
        <h1>{post.data.title}</h1>
        <p className="post-date">
          <CalendarDays size={16} /> {date(post.data.createdAt)}
        </p>
        <div className="post-content">{post.data.content}</div>
      </div>
      {kind === "qna" && (
        <div className="panel comments-panel">
          <SectionTitle
            eyebrow="DISCUSSION"
            title={`댓글 ${comments.data?.length ?? 0}`}
          />
          {(comments.data || []).map((comment) => (
            <div
              className={`comment ${comment.parentId ? "reply" : ""}`}
              key={comment.id}
            >
              <span className="comment-avatar">
                {comment.authorName?.charAt(0)}
              </span>
              <div>
                <div className="comment-meta">
                  <strong>{comment.authorName}</strong>
                  <small>{shortDate(comment.createdAt)}</small>
                </div>
                <p>{comment.content}</p>
                <button
                  onClick={() => {
                    setReplyTo(comment.id);
                    document.getElementById("comment-input")?.focus();
                  }}
                >
                  답글 달기
                </button>
              </div>
            </div>
          ))}
          {!comments.loading && !comments.data?.length && (
            <p className="muted">첫 댓글을 남겨보세요.</p>
          )}
          <form className="comment-form" onSubmit={send}>
            {replyTo && (
              <span className="reply-hint">
                답글 작성 중{" "}
                <button type="button" onClick={() => setReplyTo(null)}>
                  <X size={14} />
                </button>
              </span>
            )}
            <textarea
              id="comment-input"
              value={content}
              onChange={(event) => setContent(event.target.value)}
              maxLength={3000}
              required
              placeholder={
                user
                  ? "따뜻한 댓글을 남겨주세요."
                  : "로그인 후 댓글을 작성할 수 있어요."
              }
            />
            <button className="button button-dark" disabled={!content.trim()}>
              <Send size={16} /> 댓글 등록
            </button>
          </form>
        </div>
      )}
    </>
  );
}

// 로그인한 회원의 프로필과 주요 활동 링크를 표시한다.
function AccountPage({ user, openAuth, onLogout, navigate }) {
  if (!user)
    return (
      <div className="account-guest">
        <span className="account-guest-icon">
          <UserRound size={42} />
        </span>
        <h1>함께 움직여 볼까요?</h1>
        <p>로그인하고 나의 운동 일정과 정보를 한곳에서 관리하세요.</p>
        <button className="button button-dark" onClick={openAuth}>
          로그인 / 회원가입 <ArrowRight size={18} />
        </button>
      </div>
    );
  return (
    <>
      <div className="page-intro intro-account">
        <div>
          <span className="eyebrow">MY SPORTMAP</span>
          <h1>
            {user.fullName}님,
            <br />
            <em>오늘도 좋은 하루예요.</em>
          </h1>
          <p>
            운동과 함께하는 일상을 응원합니다.
            <br />
            나의 정보를 확인하고 새로운 운동을 찾아보세요.
          </p>
        </div>
        <div className="account-art">
          <UserRound size={98} strokeWidth={1.2} />
        </div>
      </div>
      <div className="account-grid">
        <div className="panel">
          <SectionTitle eyebrow="MY PROFILE" title="내 정보" />
          <div className="profile-avatar">{user.fullName?.charAt(0)}</div>
          <div className="info-list">
            <div>
              <span>성명</span>
              <strong>{user.fullName}</strong>
            </div>
            <div>
              <span>아이디</span>
              <strong>{user.username}</strong>
            </div>
            <div>
              <span>생년월일</span>
              <strong>{user.birthDate}</strong>
            </div>
            <div>
              <span>현재 만 나이</span>
              <strong>{user.age ?? "—"}세</strong>
            </div>
            <div>
              <span>이메일</span>
              <strong>{user.email}</strong>
            </div>
            <div>
              <span>전화번호</span>
              <strong>{formatPhone(user.phoneNumber)}</strong>
            </div>
            <div>
              <span>성별</span>
              <strong>
                {{ MALE: "남성", FEMALE: "여성", OTHER: "기타" }[user.gender] ||
                  user.gender}
              </strong>
            </div>
          </div>
        </div>
        <div className="account-actions">
          <button onClick={() => navigate("/fitness")}>
            <span className="stat-icon mint">
              <HeartPulse size={23} />
            </span>
            <strong>나의 체력 분석</strong>
            <p>결과지로 참고 등급을 확인해요.</p>
            <ArrowUpRight size={19} />
          </button>
          <button onClick={() => navigate("/reservations")}>
            <span className="stat-icon lilac">
              <Ticket size={23} />
            </span>
            <strong>내 예약 확인하기</strong>
            <p>신청한 프로그램과 예약 내역을 확인해요.</p>
            <ArrowUpRight size={19} />
          </button>
          <button onClick={onLogout}>
            <span className="stat-icon peach">
              <LogOut size={23} />
            </span>
            <strong>로그아웃</strong>
            <p>안전하게 접속을 종료합니다.</p>
            <ArrowUpRight size={19} />
          </button>
        </div>
      </div>
    </>
  );
}

// 회원가입과 로그인 입력을 검증하고 인증 API를 호출한다.
function AuthModal({ close, onSuccess, notify, initialMode = "login" }) {
  const [mode, setMode] = useState(initialMode);
  const [form, setForm] = useState({
    fullName: "",
    username: "",
    password: "",
    birthDate: "",
    email: "",
    phoneNumber: "",
    gender: "OTHER",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [emailSent, setEmailSent] = useState(false);
  const [emailRequestToken, setEmailRequestToken] = useState("");
  const [emailVerificationToken, setEmailVerificationToken] = useState("");
  const [emailBusy, setEmailBusy] = useState(false);
  const [emailMessage, setEmailMessage] = useState("");
  const update = (event) => {
    if (event.target.name === "email") {
      setEmailSent(false);
      setEmailRequestToken("");
      setEmailVerificationToken("");
      setEmailMessage("");
    }
    setForm((previous) => ({
      ...previous,
      [event.target.name]: event.target.value,
    }));
  };

  // 입력한 주소로 버튼 방식의 인증 메일 발송을 요청한다.
  async function sendEmailCode() {
    setEmailBusy(true);
    setError("");
    setEmailMessage("");
    try {
      const result = await api("/api/users/email/send", {
        method: "POST",
        body: json({ email: form.email }),
      });
      setEmailSent(true);
      setEmailRequestToken(result.requestToken);
      setEmailVerificationToken("");
      setEmailMessage(
        "인증 메일을 보냈습니다. 메일 안의 인증 버튼을 눌러 주세요.",
      );
    } catch (caught) {
      setError(caught.message);
    } finally {
      setEmailBusy(false);
    }
  }

  // 메일의 버튼 클릭 여부를 주기적으로 확인해 회원가입을 활성화한다.
  useEffect(() => {
    if (!emailSent || !emailRequestToken || emailVerificationToken) return;
    let active = true;
    const check = async () => {
      try {
        const result = await api("/api/users/email/status", {
          method: "POST",
          body: json({ email: form.email, requestToken: emailRequestToken }),
        });
        if (active && result.verified) {
          setEmailVerificationToken(result.verificationToken);
          setEmailMessage("이메일 인증이 완료됐습니다.");
        }
      } catch (caught) {
        if (active && caught.status !== 429) setError(caught.message);
      }
    };
    check();
    const timer = window.setInterval(check, 2500);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [emailSent, emailRequestToken, emailVerificationToken, form.email]);

  async function submit(event) {
    event.preventDefault();
    if (mode === "signup" && !emailVerificationToken) {
      setError("이메일 인증을 완료해 주세요.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      if (mode === "signup") {
        await api("/api/users/signup", {
          method: "POST",
          body: json({ ...form, emailVerificationToken }),
        });
        notify("회원가입이 완료됐습니다. 로그인해 주세요.");
        setMode("login");
      } else {
        const user = await api("/api/users/login", {
          method: "POST",
          body: json({ username: form.username, password: form.password }),
        });
        onSuccess(user);
        close();
        notify(`${user.fullName}님, 반갑습니다!`);
      }
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="modal-backdrop" onMouseDown={close}>
      <div
        className="modal auth-modal"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button className="modal-close" onClick={close} aria-label="닫기">
          <X size={20} />
        </button>
        <span className="auth-mark">
          <Navigation size={24} fill="currentColor" />
        </span>
        <span className="eyebrow">WELCOME TO SPORTMAP</span>
        <h2>
          {mode === "login" ? "다시 만나서 반가워요." : "함께 움직여 볼까요?"}
        </h2>
        <p>
          {mode === "login"
            ? "운동을 시작하는 가장 쉬운 방법, SportMap."
            : "몇 가지 정보만 입력하면 시작할 수 있어요."}
        </p>
        <form onSubmit={submit}>
          {mode === "signup" && (
            <>
              <label>
                성명
                <input
                  name="fullName"
                  value={form.fullName}
                  onChange={update}
                  required
                  maxLength={50}
                  placeholder="홍길동"
                  lang="ko"
                />
              </label>
            </>
          )}
          <label>
            아이디
            <input
              name="username"
              value={form.username}
              onChange={update}
              required
              minLength={4}
              maxLength={30}
              pattern={
                mode === "signup" ? "[A-Za-z][A-Za-z0-9_]{3,29}" : undefined
              }
              title={
                mode === "signup"
                  ? "영문자로 시작하는 영문·숫자·밑줄 4~30자"
                  : undefined
              }
              placeholder="영문자로 시작하는 아이디"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              lang="en"
              inputMode="text"
            />
            {mode === "signup" && (
              <small className="input-hint">
                영문자로 시작하고 영문·숫자·_만 사용해 주세요. 한글 입력
                상태라면 한/영 키로 전환해 주세요.
              </small>
            )}
          </label>
          <label>
            비밀번호
            <input
              name="password"
              value={form.password}
              onChange={update}
              required
              minLength={mode === "signup" ? 8 : undefined}
              maxLength={72}
              type="password"
              placeholder="비밀번호를 입력하세요"
              autoComplete={
                mode === "login" ? "current-password" : "new-password"
              }
            />
          </label>
          {mode === "signup" && (
            <>
              <div className="form-two">
                <label>
                  생년월일
                  <input
                    name="birthDate"
                    value={form.birthDate}
                    onChange={update}
                    required
                    type="date"
                    max={new Date().toISOString().slice(0, 10)}
                  />
                </label>
                <label>
                  성별
                  <select name="gender" value={form.gender} onChange={update}>
                    <option value="OTHER">기타</option>
                    <option value="MALE">남성</option>
                    <option value="FEMALE">여성</option>
                  </select>
                </label>
              </div>
              <label>
                이메일
                <input
                  name="email"
                  value={form.email}
                  onChange={update}
                  required
                  type="email"
                  placeholder="name@example.com"
                />
              </label>
              <button
                className="email-action"
                type="button"
                disabled={
                  emailBusy ||
                  !form.email ||
                  !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)
                }
                onClick={sendEmailCode}
              >
                {emailBusy
                  ? "처리 중..."
                  : emailSent
                    ? "인증 메일 다시 보내기"
                    : "이메일 인증하기"}
              </button>
              {emailMessage && (
                <small className="email-status">{emailMessage}</small>
              )}
              <label>
                전화번호
                <input
                  name="phoneNumber"
                  value={form.phoneNumber}
                  onChange={update}
                  required
                  placeholder="010-1234-5678"
                />
              </label>
            </>
          )}
          {error && <div className="form-error">{error}</div>}
          <button className="button button-dark wide" disabled={busy}>
            {busy ? "처리 중..." : mode === "login" ? "로그인" : "회원가입"}{" "}
            <ArrowRight size={17} />
          </button>
        </form>
        <div className="auth-switch">
          {mode === "login" ? "처음 오셨나요?" : "이미 계정이 있나요?"}{" "}
          <button
            onClick={() => {
              setMode(mode === "login" ? "signup" : "login");
              setError("");
            }}
          >
            {mode === "login" ? "회원가입하기" : "로그인하기"}
          </button>
        </div>
        {mode === "login" && (
          <div className="demo-hint">
            <Sparkles size={15} /> 로컬 데모: demo_user / Demo1234!
          </div>
        )}
      </div>
    </div>
  );
}

// 자체 예약 가능 프로그램의 시간을 입력받아 예약한다.
function BookingModal({ program, close, notify, onBooked }) {
  const future = (hours = 0) => {
    const d = new Date(Date.now() + 7 * 86400000 + hours * 3600000);
    return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
      .toISOString()
      .slice(0, 16);
  };
  const [startsAt, setStartsAt] = useState(future());
  const [endsAt, setEndsAt] = useState(future(1));
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    try {
      await api("/api/reservations", {
        method: "POST",
        body: json({
          programId: program.id,
          startsAt: new Date(startsAt).toISOString(),
          endsAt: new Date(endsAt).toISOString(),
        }),
      });
      notify("예약이 확정되었습니다.");
      onBooked();
      close();
    } catch (error) {
      notify(error.message, "error");
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="modal-backdrop" onMouseDown={close}>
      <div
        className="modal booking-modal"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button className="modal-close" onClick={close}>
          <X size={20} />
        </button>
        <span className="eyebrow">BOOK A SPOT</span>
        <h2>예약 시간 선택</h2>
        <p>
          <strong>{program.name}</strong>의 이용 시간을 선택해 주세요.
        </p>
        <form onSubmit={submit}>
          <label>
            시작 시간
            <input
              type="datetime-local"
              value={startsAt}
              onChange={(event) => setStartsAt(event.target.value)}
              required
            />
          </label>
          <label>
            종료 시간
            <input
              type="datetime-local"
              value={endsAt}
              onChange={(event) => setEndsAt(event.target.value)}
              required
            />
          </label>
          <div className="booking-note">
            <CircleHelp size={17} /> 정원 {program.capacity ?? "미정"}명 ·
            시간이 겹치는 예약은 정원 기준으로 제한됩니다.
          </div>
          <button className="button button-dark wide" disabled={busy}>
            {busy ? "예약 중..." : "예약 확정하기"} <ArrowRight size={17} />
          </button>
        </form>
      </div>
    </div>
  );
}

// 현재 경로에 맞는 화면을 선택하고 전역 인증·알림 상태를 관리한다.
export default function App() {
  const [path, navigate] = useRoute();
  const [user, setUser] = useState(null);
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState("login");
  const [booking, setBooking] = useState(null);
  const [mobileMenu, setMobileMenu] = useState(false);
  const [toast, setToast] = useState(null);
  const notify = useCallback(
    (message, tone = "success") => setToast({ message, tone, id: Date.now() }),
    [],
  );
  useEffect(() => {
    api("/api/users/mypage")
      .then(setUser)
      .catch(() => setUser(null));
  }, []);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4500);
    return () => clearTimeout(timer);
  }, [toast]);
  const requireAuth = () => {
    if (user) return true;
    setAuthOpen(true);
    return false;
  };
  async function onApply(program) {
    if (!requireAuth()) return;
    if (program.bookingSupported) {
      setBooking(program);
      return;
    }
    try {
      await api("/api/reservations", {
        method: "POST",
        body: json({ programId: program.id }),
      });
      notify("신청 내역을 기록했습니다. 운영기관 접수는 별도로 확인해 주세요.");
    } catch (error) {
      notify(error.message, "error");
    }
  }
  async function onLogout() {
    try {
      await api("/api/users/logout", { method: "POST" });
      setUser(null);
      resetCsrf();
      notify("로그아웃했습니다.");
      navigate("/");
    } catch (error) {
      notify(error.message, "error");
    }
  }
  const facilityMatch = path.match(/^\/facilities\/(\d+)$/);
  const programMatch = path.match(/^\/programs\/(\d+)$/);
  const postMatch = path.match(/^\/community\/(notices|qna)\/(\d+)$/);
  let content;
  if (facilityMatch)
    content = (
      <FacilityDetailPage
        id={facilityMatch[1]}
        navigate={navigate}
        notify={notify}
        requireAuth={requireAuth}
      />
    );
  else if (programMatch)
    content = (
      <ProgramDetailPage
        id={programMatch[1]}
        navigate={navigate}
        onApply={onApply}
      />
    );
  else if (postMatch)
    content = (
      <CommunityDetailPage
        kind={postMatch[1]}
        id={postMatch[2]}
        navigate={navigate}
        user={user}
        openAuth={() => setAuthOpen(true)}
        notify={notify}
      />
    );
  else if (path === "/live-facilities")
    content = <LiveFacilitiesPage navigate={navigate} />;
  else if (path === "/programs")
    content = <ProgramsPage navigate={navigate} onApply={onApply} />;
  else if (path === "/recommendations") content = <RecommendationsPage />;
  else if (path === "/fitness")
    content = (
      <FitnessPage
        user={user}
        openAuth={() => setAuthOpen(true)}
        notify={notify}
      />
    );
  else if (path === "/fitness-centers") content = <FitnessCentersPage />;
  else if (path === "/admin")
    content =
      user?.role === "ADMIN" ? (
        <AdminPage notify={notify} />
      ) : (
        <div className="page-section">
          <h1>관리자 전용 화면입니다.</h1>
          <p>관리자 계정으로 로그인해 주세요.</p>
        </div>
      );
  else if (path === "/reservations")
    content = (
      <ReservationsPage
        user={user}
        openAuth={() => setAuthOpen(true)}
        notify={notify}
      />
    );
  else if (path === "/community")
    content = (
      <CommunityPage
        navigate={navigate}
        user={user}
        openAuth={() => setAuthOpen(true)}
        notify={notify}
      />
    );
  else if (path === "/account")
    content = (
      <AccountPage
        user={user}
        openAuth={() => setAuthOpen(true)}
        onLogout={onLogout}
        navigate={navigate}
      />
    );
  else
    content = (
      <FacilitiesPage
        navigate={navigate}
        user={user}
        openAuth={() => setAuthOpen(true)}
      />
    );
  return (
    <div className="app-shell">
      <Sidebar
        path={path}
        navigate={navigate}
        user={user}
        openAuth={() => setAuthOpen(true)}
        mobileMenu={mobileMenu}
        setMobileMenu={setMobileMenu}
      />
      <div className="main-wrap">
        <Header
          path={path}
          navigate={navigate}
          user={user}
          openAuth={() => {
            setAuthMode("login");
            setAuthOpen(true);
          }}
          openSignup={() => {
            setAuthMode("signup");
            setAuthOpen(true);
          }}
          onLogout={onLogout}
          mobileMenu={mobileMenu}
          setMobileMenu={setMobileMenu}
        />
        <main className="main-content">
          {content}
          <footer className="footer">
            <span>
              © {new Date().getFullYear()} SportMap. 오늘의 움직임을 응원합니다.
            </span>
            <span>
              Made with public sports data <HeartPulse size={14} />
            </span>
          </footer>
        </main>
      </div>
      {authOpen && (
        <AuthModal
          initialMode={authMode}
          close={() => setAuthOpen(false)}
          onSuccess={(member) => {
            setUser(member);
            if (member.role === "ADMIN") navigate("/admin");
          }}
          notify={notify}
        />
      )}
      {booking && (
        <BookingModal
          program={booking}
          close={() => setBooking(null)}
          notify={notify}
          onBooked={() => navigate("/reservations")}
        />
      )}
      {toast && (
        <div className={`toast ${toast.tone}`} key={toast.id}>
          {toast.tone === "success" ? (
            <Check size={18} />
          ) : (
            <CircleHelp size={18} />
          )}
          {toast.message}
          <button onClick={() => setToast(null)}>
            <X size={16} />
          </button>
        </div>
      )}
    </div>
  );
}
