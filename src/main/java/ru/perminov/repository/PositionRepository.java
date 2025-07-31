package ru.perminov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.perminov.model.Position;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    
    Optional<Position> findByFigi(String figi);
    
    List<Position> findByInstrumentType(String instrumentType);
    
    List<Position> findByCurrency(String currency);
    
    List<Position> findByTicker(String ticker);
} 