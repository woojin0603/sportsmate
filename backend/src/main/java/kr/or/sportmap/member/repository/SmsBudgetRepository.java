package kr.or.sportmap.member.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import kr.or.sportmap.member.domain.SmsBudget;
import org.springframework.data.jpa.repository.*;

public interface SmsBudgetRepository extends JpaRepository<SmsBudget, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from SmsBudget b where b.id = 1")
  Optional<SmsBudget> lockBudget();
}
