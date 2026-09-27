package kr.or.sportmap.recommendation.domain;

import jakarta.persistence.*;

/** 체육 서비스의 데이터를 표현하고 관계를 보존한다. */
@Entity
@Table(name = "exercise_recommendations")
public class ExerciseRecommendation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Integer minAge, maxAge;

  @Column(nullable = false, length = 120)
  public String exerciseName;

  @Column(length = 1000)
  public String description;

  @Column(length = 80)
  public String datasetCode;

  @Column(length = 200)
  public String sourceKey;

  @Column(length = 30)
  public String bmiCategory;

  @Column(length = 1)
  public String sex;

  @Column(length = 20)
  public String fitnessGrade;

  @Column(length = 20)
  public String exerciseStep;

  public Integer recommendationRank;

  protected ExerciseRecommendation() {}

  public ExerciseRecommendation(
    int minAge,
    int maxAge,
    String exerciseName,
    String description
  ) {
    this.minAge = minAge;
    this.maxAge = maxAge;
    this.exerciseName = exerciseName;
    this.description = description;
    this.datasetCode = "DEMO";
  }

  /** 연령별 운동정보 원본의 한 행을 저장한다. */
  public static ExerciseRecommendation fromDataset(
    int minAge,
    int maxAge,
    String exerciseName,
    String bmiCategory,
    String sex,
    String fitnessGrade,
    String exerciseStep,
    int recommendationRank,
    String sourceKey
  ) {
    ExerciseRecommendation item = new ExerciseRecommendation(
      minAge,
      maxAge,
      exerciseName,
      null
    );
    item.datasetCode = "AGE_EXERCISE_2026";
    item.sourceKey = sourceKey;
    item.bmiCategory = bmiCategory;
    item.sex = sex;
    item.fitnessGrade = fitnessGrade;
    item.exerciseStep = exerciseStep;
    item.recommendationRank = recommendationRank;
    return item;
  }
}
