# SportMap Backend

Spring Boot 3.3, Java 17, JPA, Querydsl 기반 API 서버입니다.

## IntelliJ 실행

1. IntelliJ에서 이 폴더의 `build.gradle`을 엽니다.
2. Gradle 동기화 후 `kr.or.sportmap.SportmapApplication`을 실행합니다.
3. 작업 디렉터리는 저장소 루트 `sportmap`으로 설정합니다. 그래야 `backend/config`과 기존 H2 DB를 동일하게 사용합니다.

## 명령행 실행

```powershell
cd backend
.\gradlew.bat bootRun
```

로컬 비밀값은 Git에서 제외되는 `config/local-secrets.properties`에 저장합니다. 예시는 `config/local-secrets.properties.example`을 참고하세요. 루트의 `tools/start_local.ps1`, `tools/configure_public_api.ps1`, `tools/configure_gmail.ps1`을 사용하면 경로를 직접 지정할 필요가 없습니다.

웹 빌드본은 `web`에서 `npm run build:spring`을 실행하면 이 프로젝트의 `src/main/resources/static`으로 복사됩니다.
