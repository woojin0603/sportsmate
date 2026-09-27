package kr.or.sportmap.fitness.repository;

import java.util.List;
import kr.or.sportmap.fitness.domain.FitnessAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface FitnessAssessmentRepository
  extends JpaRepository<FitnessAssessment, Long>
{
  List<FitnessAssessment> findByMemberIdOrderByMeasuredOnDescIdDesc(
    Long memberId
  );
}
