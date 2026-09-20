package kr.or.sportmap.member.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import kr.or.sportmap.member.domain.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 이메일별 인증 상태를 잠금 조회해 중복 사용을 막는다. */
public interface EmailVerificationRepository
  extends JpaRepository<EmailVerification, Long>
{
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
    "select verification from EmailVerification verification where verification.email = :email"
  )
  Optional<EmailVerification> findByEmailForUpdate(
    @Param("email") String email
  );

  Optional<EmailVerification> findByConfirmationHash(String confirmationHash);
}
