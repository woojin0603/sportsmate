package kr.or.sportmap.recommendation.controller;

import kr.or.sportmap.recommendation.repository.ExerciseRecommendationRepository;
import org.springframework.web.bind.annotation.*;

/** 화면과 외부 클라이언트의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

  private final ExerciseRecommendationRepository repository;

  public RecommendationController(ExerciseRecommendationRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public Object forAge(
    @RequestParam int age,
    @RequestParam(defaultValue = "정상") String bmi,
    @RequestParam(defaultValue = "F") String sex,
    @RequestParam(defaultValue = "참가증") String grade
  ) {
    if (age < 0 || age > 120) throw new IllegalArgumentException(
      "age must be 0..120"
    );
    if (
      !java.util.Set.of(
        "저체중",
        "정상",
        "비만전단계비만",
        "1단계비만",
        "2단계비만",
        "3단계비만"
      ).contains(bmi)
    ) throw new IllegalArgumentException("invalid bmi category");
    if (
      !java.util.Set.of("F", "M").contains(sex)
    ) throw new IllegalArgumentException("invalid sex");
    if (
      !java.util.Set.of("1등급", "2등급", "3등급", "참가증").contains(grade)
    ) throw new IllegalArgumentException("invalid fitness grade");
    return repository.findFromDataset(age, bmi, sex, grade);
  }
}
