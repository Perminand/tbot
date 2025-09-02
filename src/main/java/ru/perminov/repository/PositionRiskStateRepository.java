package ru.perminov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.perminov.model.PositionRiskState;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRiskStateRepository extends JpaRepository<PositionRiskState, Long> {
    
    Optional<PositionRiskState> findByAccountIdAndFigiAndSide(
        String accountId, String figi, PositionRiskState.PositionSide side);
    
    List<PositionRiskState> findByAccountId(String accountId);
    
    List<PositionRiskState> findByAccountIdAndSide(String accountId, PositionRiskState.PositionSide side);
    
    @Query("SELECT prs FROM PositionRiskState prs WHERE prs.accountId = :accountId AND prs.figi = :figi")
    List<PositionRiskState> findByAccountIdAndFigi(@Param("accountId") String accountId, @Param("figi") String figi);
    
    @Query("SELECT prs FROM PositionRiskState prs WHERE prs.stopLossLevel IS NOT NULL AND prs.stopLossLevel > 0")
    List<PositionRiskState> findActiveStopLosses();
    
    @Query("SELECT prs FROM PositionRiskState prs WHERE prs.takeProfitLevel IS NOT NULL AND prs.takeProfitLevel > 0")
    List<PositionRiskState> findActiveTakeProfits();
}
