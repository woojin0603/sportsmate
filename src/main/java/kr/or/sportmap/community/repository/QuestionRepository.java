package kr.or.sportmap.community.repository;

import java.util.List;
import java.util.Optional;
import kr.or.sportmap.community.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface QuestionRepository extends JpaRepository<Question, Long> {
  List<Question> findTop20ByAuthorIdOrderByIdDesc(Long authorId);

  Optional<Question> findByIdAndAuthorId(Long id, Long authorId);
}
