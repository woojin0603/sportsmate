package kr.or.sportmap.member.repository;

import java.util.Optional;
import kr.or.sportmap.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface MemberRepository extends JpaRepository<Member, Long> {
  Optional<Member> findByUsername(String username);
  boolean existsByUsername(String username);
  boolean existsByEmail(String email);
}
