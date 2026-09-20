# 국민체력100 결과지 참고 평가

## 평가 원칙

- 회원 생년월일로 현재 만 나이를 응답합니다. 결과지 평가는 **검사일 당시 만 나이**를 사용합니다.
- 유소년(만 11~12세), 청소년(13~18세), 성인(19~64세), 어르신(65세 이상)으로 구분합니다. 유아기와 만 7~10세는 A~D 평가 대상에서 제외합니다.
- [국민체력100 공식 인증기준](https://nfa.kspo.or.kr/reserve/0/selectMeasureGradeItemListByAgeSe.kspo)의 2026-09-13 기준표를 `fitness-standards.json`, `fitness-body-composition.json`에 고정했습니다. 성별·검사 당시 나이 구간별로 항목과 경계값을 선택하며, 구간 사이를 보간하지 않습니다.
- 종목별로 공식 1·2·3등급 경계값을 통과하면 각각 100·75·50점, 미달이면 25점으로 표시합니다. 시간 종목은 **작을수록 우수**합니다. 전체 점수는 확인된 필수 항목의 참고 평균입니다.
- 전체 A/B/C 판정은 공식 1/2/3등급의 항목 조합 기준을 따라 계산합니다. 나머지는 D로 묶습니다. **A~D와 0~100점은 SportMap 자체 표시이며 국민체력100의 공식 인증등급·점수가 아닙니다.** PDF에 공식 등급만 있고 수치가 부족하면 1→A, 2→B, 3→C, 4~6→D로 변환합니다. 유소년 참가증도 D로 표시합니다.
- 필수 측정값 또는 3등급에 필요한 신체조성 값이 없으면 임의로 계산하지 않고 `NEEDS_REVIEW`를 반환합니다. 결과지 등급과 수치 재산출이 불일치해도 저장을 보류합니다.

## API

인증된 회원만 접근합니다. 브라우저 쓰기 요청은 기존 CSRF 헤더를 사용합니다.

| 요청 | 역할 |
|---|---|
| `POST /api/fitness/parse` (`multipart/form-data`, `file`) | PDF에서 검사일·공식 등급·수치를 추출하고 미리보기 반환 |
| `POST /api/fitness/preview` (JSON) | 사용자가 확인·수정한 수치를 다시 평가 |
| `POST /api/fitness/assessments` (JSON) | `READY`인 평가만 저장 |
| `GET /api/fitness/assessments` | 본인 평가 이력 조회 |

`preview`와 `assessments` 요청 본문:

```json
{
  "measuredOn": "2026-09-13",
  "reportedOfficialGrade": null,
  "values": {
    "shuttle20": 49,
    "grip": 62.6,
    "sitUp": 47,
    "flexibility": 14.2,
    "longJump": 219
  }
}
```

PDF는 10MB·5쪽 이하만 처리합니다. 텍스트형 PDF는 PDFBox로 추출합니다. 이미지 스캔본은 서버에 [Tesseract OCR](https://github.com/tesseract-ocr/tessdoc) 실행 파일과 [한국어 `kor.traineddata`](https://github.com/tesseract-ocr/tessdata/blob/main/kor.traineddata), 영어 데이터가 설치되어 있어야 합니다. Windows 개발 PC에서는 `python tools/install_ocr.py`를 프로젝트 루트에서 실행하면 공식 배포본과 두 언어 데이터를 `.local-tools/tesseract`에 설치하고, Git에서 제외된 `config/local-secrets.properties`에 실행 경로를 설정합니다. 설치 또는 설정을 바꾼 뒤 Spring Boot를 재시작하세요. 원본 PDF와 OCR 임시 파일은 보관하지 않습니다.

스캔 이미지의 인식에는 `--psm 6`을 사용하고 한글 음절 사이에 잘못 들어간 공백을 정리합니다. OCR은 결과지의 품질과 서식에 영향을 받으므로 추출된 날짜·종목·수치를 원본과 대조한 뒤 저장해야 합니다.

**검증 범위:** 공식 표의 44개 연령·성별 구간과 경계값의 단조성을 검사했고, React 빌드를 확인했습니다. 이 실행 환경의 Java ZipFS 파일 접근 오류로 Gradle 테스트 실행은 완료하지 못했습니다. 실제 국민체력100 결과지 샘플이 제공되지 않아 PDF 별 서식·OCR 추출 정확도는 검증 전입니다. 샘플 결과지로 필드 위치와 단위를 확인한 뒤 파서 패턴을 보정해야 합니다.
