package kr.or.sportmap.recommendation.repository;

import java.util.List;
import kr.or.sportmap.recommendation.domain.ExerciseRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface ExerciseRecommendationRepository
  extends JpaRepository<ExerciseRecommendation, Long>
{
  @Query(
    "select e from ExerciseRecommendation e where e.minAge <= :age and e.maxAge >= :age order by e.exerciseName"
  )
  List<ExerciseRecommendation> findForAge(@Param("age") int age);

  long countByDatasetCode(String datasetCode);

  /** 원본의 다섯 조건에 맞는 준비·본·마무리 운동을 순서대로 조회한다. */
  @Query(
    "select e from ExerciseRecommendation e where e.datasetCode = 'AGE_EXERCISE_2026' and e.minAge <= :age and e.maxAge >= :age and e.bmiCategory = :bmi and e.sex = :sex and e.fitnessGrade = :grade order by case e.exerciseStep when '준비운동' then 1 when '본운동' then 2 else 3 end, e.recommendationRank"
  )
  List<ExerciseRecommendation> findFromDataset(
    @Param("age") int age,
    @Param("bmi") String bmi,
    @Param("sex") String sex,
    @Param("grade") String grade
  );
}
