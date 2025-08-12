package ru.perminov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.perminov.model.RiskRule;

import java.util.Optional;

public interface RiskRuleRepository extends JpaRepository<RiskRule, Long> {
    Optional<RiskRule> findByFigi(String figi);
}


