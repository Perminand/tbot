package ru.perminov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.perminov.model.RiskEvent;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RiskEventRepository extends JpaRepository<RiskEvent, Long> {
    
    List<RiskEvent> findByAccountIdAndFigiOrderByCreatedAtDesc(String accountId, String figi);
    
    List<RiskEvent> findByAccountIdOrderByCreatedAtDesc(String accountId);
    
    List<RiskEvent> findByEventTypeOrderByCreatedAtDesc(RiskEvent.EventType eventType);
    
    @Query("SELECT re FROM RiskEvent re WHERE re.createdAt >= :since ORDER BY re.createdAt DESC")
    List<RiskEvent> findRecentEvents(@Param("since") LocalDateTime since);
    
    @Query("SELECT re FROM RiskEvent re WHERE re.accountId = :accountId AND re.figi = :figi AND re.createdAt >= :since ORDER BY re.createdAt DESC")
    List<RiskEvent> findRecentEventsByPosition(@Param("accountId") String accountId, @Param("figi") String figi, @Param("since") LocalDateTime since);
}
