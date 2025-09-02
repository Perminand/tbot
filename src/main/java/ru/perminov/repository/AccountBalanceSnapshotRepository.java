package ru.perminov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.perminov.model.AccountBalanceSnapshot;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AccountBalanceSnapshotRepository extends JpaRepository<AccountBalanceSnapshot, Long> {
    List<AccountBalanceSnapshot> findByAccountIdAndCapturedAtBetweenOrderByCapturedAtAsc(String accountId, LocalDateTime from, LocalDateTime to);
    List<AccountBalanceSnapshot> findTop48ByAccountIdOrderByCapturedAtDesc(String accountId);
}


