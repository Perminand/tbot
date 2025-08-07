package ru.perminov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.perminov.model.TradingSettings;

import java.util.Optional;

@Repository
public interface TradingSettingsRepository extends JpaRepository<TradingSettings, Long> {
    
    Optional<TradingSettings> findByKey(String key);
    
    boolean existsByKey(String key);
}

