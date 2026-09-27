package kr.or.sportmap.community.repository;

import kr.or.sportmap.community.domain.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

/** 데이터베이스 조회와 저장을 담당한다. */
public interface NoticeRepository extends JpaRepository<Notice, Long> {}
