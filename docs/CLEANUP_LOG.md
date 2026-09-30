# 프로젝트 정리 기록

정리일: 2026-09-30

## 삭제한 폴더

| 경로 | 크기 | 정리 사유 |
|---|---:|---|
| `frontend/` | 약 86.29MB | 소스와 `package.json` 없이 이전 `dist`와 `node_modules`만 남은 중복 웹 폴더 |
| `mobile/` | 약 0.36MB | 소스 없이 `.gradle`, `app/build`, 디버그 APK, `local.properties`만 남은 Android 빌드 잔여 폴더 |
| `web/.npm-cache-codex/` | 약 18.64MB | 빌드 검증 과정에서 생성된 임시 npm 캐시 |

총 정리 용량은 약 105.29MB다.

현재 사용하는 프로젝트는 `web/`, `android/`, `backend/`이며 삭제한 폴더의 소스에 의존하지 않는다. 같은 이름의 중복 폴더와 임시 캐시가 다시 Git에 포함되지 않도록 `.gitignore`에 경로를 추가했다.

## 삭제된 중복 패키지

`frontend/node_modules` 안에 있던 패키지만 삭제했다. 현재 사용하는 `web/node_modules`와 `web/package.json`은 유지했다.

직접 패키지:

- `react`, `react-dom`
- `leaflet`, `react-leaflet`, `@react-leaflet/core`
- `lucide-react`
- `vite`, `@vitejs/plugin-react`
- `prettier`, `prettier-plugin-java`

함께 삭제된 주요 하위 패키지 그룹:

- Babel: `@babel/core`, `@babel/parser`, `@babel/traverse`, JSX 변환 플러그인과 helper 패키지
- 빌드 도구: `esbuild`, `rollup`, Windows용 esbuild·Rollup 바이너리
- CSS·번들 보조: `postcss`, `nanoid`, `picocolors`, `tinyglobby`, `picomatch`
- 브라우저 호환 데이터: `browserslist`, `caniuse-lite`, `electron-to-chromium`
- React 빌드 보조: `react-refresh`, `scheduler`
- 소스맵·파서: `@jridgewell/*`, `source-map-js`, `@types/*`, `web-tree-sitter`

## 유지한 패키지

`web/package.json`에 선언된 패키지는 소스와 빌드 설정에서 모두 사용 중이다. `npm prune --dry-run` 결과도 제거 대상이 없었으므로 활성 웹 프로젝트에서는 패키지를 삭제하지 않았다.

| 패키지 | 사용처 |
|---|---|
| `react`, `react-dom` | 웹 UI와 앱 진입점 |
| `leaflet`, `react-leaflet` | 체육시설 지도 |
| `lucide-react` | 웹 아이콘 |
| `vite`, `@vitejs/plugin-react` | 개발 서버와 배포 빌드 |
| `prettier`, `prettier-plugin-java` | JavaScript·CSS·Java 포맷 검사 |

## 재생 가능한 산출물

- `web/dist/`는 `npm run build`로 다시 만들 수 있으며 Git에서 제외한다.
- `android/app/build/`는 Gradle 빌드로 다시 만들 수 있으며 Git에서 제외한다.
- `backend/build/`는 Gradle 빌드로 다시 만들 수 있으며 Git에서 제외한다.
