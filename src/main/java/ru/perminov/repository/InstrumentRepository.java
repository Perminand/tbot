package ru.perminov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.perminov.model.Instrument;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, String> {
    
    Optional<Instrument> findByTicker(String ticker);
    
    List<Instrument> findByInstrumentType(String instrumentType);
    
    List<Instrument> findByCurrency(String currency);
    
    List<Instrument> findByExchange(String exchange);
} 