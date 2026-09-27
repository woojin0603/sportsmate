package kr.or.sportmap.member.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kr.or.sportmap.member.domain.PhoneVerification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PhoneVerificationRepository
  extends JpaRepository<PhoneVerification, String>
{
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from PhoneVerification v where v.id = :id")
  Optional<PhoneVerification> findLocked(@Param("id") String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from PhoneVerification v where v.phoneNumber = :phone")
  List<PhoneVerification> findPhoneLocked(@Param("phone") String phone);

  long countByClientKeyAndSentAtAfter(String clientKey, Instant since);
  void deleteBySentAtBefore(Instant cutoff);
}
