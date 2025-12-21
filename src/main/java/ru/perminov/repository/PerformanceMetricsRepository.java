package ru.perminov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.perminov.model.PerformanceMetrics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PerformanceMetricsRepository extends JpaRepository<PerformanceMetrics, Long> {
    
    /**
     * Получить последние метрики для аккаунта
     */
    Optional<PerformanceMetrics> findFirstByAccountIdOrderByCalculatedAtDesc(String accountId);
    
    /**
     * Получить все метрики для аккаунта, отсортированные по дате
     */
    List<PerformanceMetrics> findByAccountIdOrderByCalculatedAtDesc(String accountId);
    
    /**
     * Получить метрики за период
     */
    List<PerformanceMetrics> findByAccountIdAndCalculatedAtBetween(
        String accountId, 
        LocalDateTime start, 
        LocalDateTime end
    );
    
    /**
     * Получить последние N метрик для аккаунта
     */
    @Query("SELECT p FROM PerformanceMetrics p WHERE p.accountId = :accountId ORDER BY p.calculatedAt DESC")
    List<PerformanceMetrics> findRecentByAccountId(@Param("accountId") String accountId, 
                                                   org.springframework.data.domain.Pageable pageable);
}

