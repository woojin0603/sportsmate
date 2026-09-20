package kr.or.sportmap.community.repository;

import java.util.List;
import kr.or.sportmap.community.domain.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface CommentRepository extends JpaRepository<Comment, Long> {
  @EntityGraph(attributePaths = "author")
  List<Comment> findByQuestionIdOrderByCreatedAtAsc(Long questionId);
}
