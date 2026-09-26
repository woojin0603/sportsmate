# 제출용 MySQL 전환 안내

## 최초 한 번만 실행

Windows의 `MySQL80` 서비스가 실행 중인 상태에서 프로젝트 루트의 PowerShell을 열고 실행합니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\initialize_submission_mysql.ps1
```

스크립트가 묻는 값은 다음 두 가지입니다.

1. 설치할 때 정한 MySQL `root` 비밀번호
2. 제출 서버에서 사용할 SportMap 관리자 비밀번호(12자 이상)

입력값은 화면에 표시되지 않습니다. 스크립트는 `sportmap_submission` 데이터베이스, 이 데이터베이스만 사용할 수 있는 `sportmap_app` 계정, 무작위 비밀키, Git에서 제외되는 비밀 설정 파일을 만듭니다. 기존 로컬 설정의 공공 체육시설 API 키도 복사합니다.

## 실행 모드 전환

로컬 H2 모드:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\start_local.ps1
```

제출용 MySQL 모드:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\start_submission.ps1
```

최초 MySQL 실행 때 JPA가 테이블을 만들고, 관리자 계정과 공개 프로그램·시설 미리보기 데이터를 등록합니다. 제출 DB에는 로컬 H2의 테스트 회원, 예약, 게시글을 복사하지 않습니다.

## 연결 및 백업 확인

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\check_submission_mysql.ps1
powershell -ExecutionPolicy Bypass -File .\tools\backup_submission_mysql.ps1
```

백업은 `backups` 폴더에 생성되며 Git에 포함되지 않습니다.

## 실제 서버 배포 시

서버에 MySQL이 따로 있으면 `config/submission-secrets.properties`의 URL을 서버 주소로 변경하거나 `DB_URL`, `DB_USER`, `DB_PASSWORD` 환경 변수를 설정합니다. HTTPS 주소가 준비되면 `app.auth.secure-cookie=true` 또는 `SECURE_COOKIE=true`로 바꿉니다.

`config/submission-secrets.properties`는 저장소에 커밋하거나 공유하면 안 됩니다. 새 컴퓨터에서는 초기화 스크립트를 다시 실행하거나 서버의 비밀 저장소에서 같은 항목을 주입합니다.
