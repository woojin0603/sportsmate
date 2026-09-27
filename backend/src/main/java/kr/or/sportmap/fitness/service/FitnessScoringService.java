package kr.or.sportmap.fitness.service;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.*;
import kr.or.sportmap.member.domain.Member;
import org.springframework.stereotype.Service;

/** 도메인 규칙과 외부 연동 작업을 처리한다. */
@Service
public class FitnessScoringService {

  private static final Set<String> LOWER_IS_BETTER = Set.of(
    "illinois",
    "handEyeTime",
    "shuttle10",
    "reaction",
    "upGo3m",
    "figure8"
  );
  private static final Map<String, String> LABELS = Map.ofEntries(
    Map.entry("shuttle15", "15m 왕복오래달리기"),
    Map.entry("shuttle20", "20m 왕복오래달리기"),
    Map.entry("vo2", "트레드밀/스텝검사"),
    Map.entry("grip", "상대악력"),
    Map.entry("curlUp", "윗몸말아올리기"),
    Map.entry("repeatJump", "반복점프"),
    Map.entry("sitUp", "교차윗몸일으키기"),
    Map.entry("flexibility", "앉아윗몸앞으로굽히기"),
    Map.entry("sideHop", "반복옆뛰기"),
    Map.entry("longJump", "제자리멀리뛰기"),
    Map.entry("handEyeCount", "눈-손 협응력(횟수)"),
    Map.entry("handEyeTime", "눈-손 협응력(초)"),
    Map.entry("illinois", "일리노이 민첩성"),
    Map.entry("airTime", "체공시간"),
    Map.entry("shuttle10", "10m 왕복달리기"),
    Map.entry("reaction", "반응시간"),
    Map.entry("walk2min", "2분 제자리걷기"),
    Map.entry("walk6min", "6분 걷기"),
    Map.entry("chairStand", "30초 의자앉았다 일어서기"),
    Map.entry("upGo3m", "3m 검사"),
    Map.entry("figure8", "8자보행"),
    Map.entry("bmi", "BMI"),
    Map.entry("bodyFat", "체지방률"),
    Map.entry("waistHeightRatio", "허리둘레-신장비")
  );

  private final FitnessStandards standards;

  public FitnessScoringService(FitnessStandards standards) {
    this.standards = standards;
  }

  public record MetricScore(
    String key,
    String label,
    double value,
    Integer level,
    Integer points
  ) {}

  public record Result(
    String status,
    Integer ageAtTest,
    String stage,
    String grade,
    Integer score,
    Integer equivalentOfficialGrade,
    String basis,
    List<String> missing,
    List<MetricScore> metrics,
    String standardSource
  ) {}

  /** 검사일의 만 나이·성별 기준표에 측정값을 대조해 A~D 참고 등급을 산출한다. */
  public Result evaluate(
    Member member,
    LocalDate measuredOn,
    Map<String, Double> values,
    Integer reportedGrade
  ) {
    if (
      measuredOn == null ||
      measuredOn.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))) ||
      measuredOn.isBefore(member.birthDate)
    ) throw new IllegalArgumentException("올바른 검사 날짜를 입력해 주세요.");
    int age = Period.between(member.birthDate, measuredOn).getYears();
    FitnessStandards.Stage stage = FitnessStandards.Stage.forAge(age);
    if (
      member.gender == Member.Gender.OTHER
    ) throw new IllegalArgumentException(
      "현재 공개된 성별별 기준표만으로는 등급을 산출할 수 없습니다."
    );
    if (values == null) values = Map.of();
    for (Map.Entry<String, Double> entry : values.entrySet())
      if (
        entry.getValue() == null || !Double.isFinite(entry.getValue())
      ) throw new IllegalArgumentException(
        "검사 수치는 유한한 숫자여야 합니다."
      );
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      String key = entry.getKey();
      double value = entry.getValue();
      boolean time = LOWER_IS_BETTER.contains(key);
      if (
        value < -50 ||
        value > 2_000 ||
        (time && value <= 0) ||
        (key.equals("bmi") && (value < 8 || value > 80)) ||
        (key.equals("bodyFat") && (value < 0 || value > 100)) ||
        (key.equals("grip") && (value < 0 || value > 200)) ||
        (key.equals("waistHeightRatio") && (value < 0.2 || value > 1.5)) ||
        (key.equals("vo2") && (value < 0 || value > 120))
      ) throw new IllegalArgumentException(
        "검사 수치를 다시 확인해 주세요: " + LABELS.getOrDefault(key, key)
      );
    }
    Map<Integer, FitnessStandards.ThresholdRow> rows = standards.rows(
      stage,
      member.gender.name(),
      age
    );
    List<String> columns = standards.columns(stage);
    Map<String, Integer> levels = new HashMap<>();
    List<MetricScore> metricScores = new ArrayList<>();
    for (int i = 0; i < columns.size(); i++) {
      String key = columns.get(i);
      Double value = values.get(key);
      if (value == null) continue;
      int level = 4;
      for (int grade = 1; grade <= 3; grade++) {
        List<Double> thresholds = rows.get(grade).thresholds();
        if (i >= thresholds.size()) continue;
        double limit = thresholds.get(i);
        if (LOWER_IS_BETTER.contains(key) ? value <= limit : value >= limit) {
          level = grade;
          break;
        }
      }
      levels.put(key, level);
      metricScores.add(
        new MetricScore(
          key,
          LABELS.getOrDefault(key, key),
          value,
          level,
          switch (level) {
            case 1 -> 100;
            case 2 -> 75;
            case 3 -> 50;
            default -> 25;
          }
        )
      );
    }
    List<List<String>> health = healthGroups(stage);
    List<List<String>> exercise = exerciseGroups(stage);
    List<String> missing = new ArrayList<>();
    for (List<String> group : health)
      if (bestLevel(group, levels) == null) missing.add(labelGroup(group));
    if (stage == FitnessStandards.Stage.SENIOR) {
      for (List<String> group : exercise)
        if (bestLevel(group, levels) == null) missing.add(labelGroup(group));
    } else if (
      exercise.stream().allMatch(group -> bestLevel(group, levels) == null)
    ) {
      missing.add("운동체력 항목 중 1개 이상");
    }
    boolean complete = missing.isEmpty();
    Integer computed = null;
    if (complete) {
      boolean health1 = allAtLeast(health, levels, 1),
        health2 = allAtLeast(health, levels, 2);
      boolean exercise1 = exerciseAtLeast(stage, exercise, levels, 1);
      boolean exercise2 = exerciseAtLeast(stage, exercise, levels, 2);
      if (health1 && exercise1) computed = 1;
      else if (health2 && exercise2) computed = 2;
      else if (
        allAtLeast(health, levels, 3) &&
        (stage == FitnessStandards.Stage.SENIOR ||
          bodyInRange(stage, member.gender.name(), age, values))
      ) computed = 3;
      else if (
        stage == FitnessStandards.Stage.SENIOR || hasBodyValue(stage, values)
      ) computed = 4;
      else missing.add("BMI 또는 체지방률/허리둘레-신장비");
    }
    if (
      reportedGrade != null && (reportedGrade < 0 || reportedGrade > 6)
    ) throw new IllegalArgumentException(
      "결과지 인증등급은 1~6 또는 유소년 참가증이어야 합니다."
    );
    if (
      stage == FitnessStandards.Stage.CHILD &&
      reportedGrade != null &&
      reportedGrade > 3
    ) throw new IllegalArgumentException(
      "유소년기 결과지는 1~3등급 또는 참가증 기준을 사용합니다."
    );
    if (
      stage != FitnessStandards.Stage.CHILD &&
      Integer.valueOf(0).equals(reportedGrade)
    ) throw new IllegalArgumentException(
      "참가증은 유소년기 결과지에만 적용합니다."
    );
    if (
      computed != null &&
      reportedGrade != null &&
      !sameAppGrade(computed, reportedGrade)
    ) return result(
      "NEEDS_REVIEW",
      age,
      stage,
      null,
      null,
      null,
      "결과지 등급과 수치 재산출 등급이 다릅니다.",
      List.of("결과지 등급과 추출 수치 재확인"),
      metricScores
    );
    Integer equivalent = computed != null ? computed : reportedGrade;
    if (equivalent == null) return result(
      "NEEDS_REVIEW",
      age,
      stage,
      null,
      null,
      null,
      "필수 수치를 확인해야 합니다.",
      missing,
      metricScores
    );
    int score =
      computed == null
        ? officialGradePoints(equivalent)
        : (int) Math.round(
            metricScores
              .stream()
              .mapToInt(MetricScore::points)
              .average()
              .orElse(0)
          );
    String basis =
      computed != null
        ? "공식 연령·성별 경계값으로 재산출한 서비스 참고 등급"
        : reportedGrade != null && reportedGrade == 0
          ? "결과지 참가증을 D로 변환한 서비스 참고 등급"
          : "결과지에 명시된 인증등급을 A~D로 변환한 서비스 참고 등급";
    Integer official =
      reportedGrade != null && reportedGrade > 0
        ? reportedGrade
        : computed != null && computed <= 3
          ? computed
          : null;
    return result(
      "READY",
      age,
      stage,
      appGrade(equivalent),
      score,
      official,
      basis,
      List.of(),
      metricScores
    );
  }

  /** 판정 상태와 세부 측정 점수를 클라이언트 응답으로 묶는다. */
  private Result result(
    String status,
    int age,
    FitnessStandards.Stage stage,
    String grade,
    Integer score,
    Integer official,
    String basis,
    List<String> missing,
    List<MetricScore> metrics
  ) {
    return new Result(
      status,
      age,
      stage.name(),
      grade,
      score,
      official,
      basis,
      missing,
      metrics,
      standards.source()
    );
  }

  /** 국민체력100 단계에 대응하는 내부 점수 값을 반환한다. */
  private static int officialGradePoints(int grade) {
    return switch (grade) {
      case 1 -> 100;
      case 2 -> 75;
      case 3 -> 50;
      default -> 25;
    };
  }

  /** 내부 등급 숫자를 화면의 A~D 문자 등급으로 변환한다. */
  private static String appGrade(int grade) {
    return switch (grade) {
      case 1 -> "A";
      case 2 -> "B";
      case 3 -> "C";
      default -> "D";
    };
  }

  /** 두 기준 단계가 같은 화면 등급에 속하는지 확인한다. */
  private static boolean sameAppGrade(int a, int b) {
    return appGrade(a).equals(appGrade(b));
  }

  /** 누락된 검사 종목 그룹을 읽기 쉬운 이름으로 표시한다. */
  private static String labelGroup(List<String> group) {
    return String.join(
      " / ",
      group
        .stream()
        .map(x -> LABELS.getOrDefault(x, x))
        .toList()
    );
  }

  /** 검사 그룹에서 최상위 충족 수준을 찾는다. */
  private static Integer bestLevel(
    List<String> group,
    Map<String, Integer> levels
  ) {
    return group
      .stream()
      .map(levels::get)
      .filter(Objects::nonNull)
      .min(Integer::compareTo)
      .orElse(null);
  }

  /** 필수 측정값이 모두 지정 수준 이상인지 확인한다. */
  private static boolean allAtLeast(
    List<List<String>> groups,
    Map<String, Integer> levels,
    int grade
  ) {
    return groups.stream().allMatch(group -> {
      Integer n = bestLevel(group, levels);
      return n != null && n <= grade;
    });
  }

  /** 운동체력 종목이 요구 수준을 충족하는지 판정한다. */
  private static boolean exerciseAtLeast(
    FitnessStandards.Stage stage,
    List<List<String>> groups,
    Map<String, Integer> levels,
    int grade
  ) {
    if (stage == FitnessStandards.Stage.SENIOR) return allAtLeast(
      groups,
      levels,
      grade
    );
    return groups.stream().anyMatch(group -> {
      Integer n = bestLevel(group, levels);
      return n != null && n <= grade;
    });
  }

  /** 연령대별 건강체력 필수 종목 그룹을 구성한다. */
  private static List<List<String>> healthGroups(FitnessStandards.Stage stage) {
    return switch (stage) {
      case CHILD -> List.of(
        List.of("shuttle15"),
        List.of("grip"),
        List.of("curlUp"),
        List.of("flexibility")
      );
      case TEEN -> List.of(
        List.of("shuttle20", "vo2"),
        List.of("grip"),
        List.of("curlUp", "repeatJump"),
        List.of("flexibility")
      );
      case ADULT -> List.of(
        List.of("shuttle20", "vo2"),
        List.of("grip"),
        List.of("sitUp"),
        List.of("flexibility")
      );
      case SENIOR -> List.of(
        List.of("walk2min", "walk6min"),
        List.of("grip"),
        List.of("chairStand"),
        List.of("flexibility")
      );
    };
  }

  /** 연령대별 운동체력 선택 종목 그룹을 구성한다. */
  private static List<List<String>> exerciseGroups(
    FitnessStandards.Stage stage
  ) {
    return switch (stage) {
      case CHILD -> List.of(
        List.of("sideHop"),
        List.of("longJump"),
        List.of("handEyeCount")
      );
      case TEEN -> List.of(
        List.of("illinois"),
        List.of("airTime"),
        List.of("handEyeTime")
      );
      case ADULT -> List.of(
        List.of("shuttle10", "reaction"),
        List.of("longJump", "airTime")
      );
      case SENIOR -> List.of(List.of("upGo3m"), List.of("figure8"));
    };
  }

  /** 체성분 측정값이 해당 연령·성별 기준 범위에 드는지 확인한다. */
  private boolean bodyInRange(
    FitnessStandards.Stage stage,
    String gender,
    int age,
    Map<String, Double> values
  ) {
    Double bmi = values.get("bmi"),
      fat = values.get("bodyFat"),
      whtr = values.get("waistHeightRatio");
    if (stage == FitnessStandards.Stage.ADULT) return (
      (bmi != null && bmi >= 18.5 && bmi < 25) ||
      (fat != null &&
        fat > (gender.equals("MALE") ? 7 : 16) &&
        fat < (gender.equals("MALE") ? 27 : 37))
    );
    FitnessStandards.BodyRow row = standards
      .bodyRow(stage, gender, age)
      .orElse(null);
    if (row == null) return false;
    return (
      (bmi != null && bmi < row.bmiMax()) ||
      (stage == FitnessStandards.Stage.CHILD
        ? whtr != null && whtr < row.otherMax()
        : fat != null && fat < row.otherMax())
    );
  }

  /** 체성분 판정에 사용할 수 있는 측정값이 있는지 확인한다. */
  private static boolean hasBodyValue(
    FitnessStandards.Stage stage,
    Map<String, Double> values
  ) {
    return (
      values.containsKey("bmi") ||
      values.containsKey(
        stage == FitnessStandards.Stage.CHILD ? "waistHeightRatio" : "bodyFat"
      )
    );
  }
}
