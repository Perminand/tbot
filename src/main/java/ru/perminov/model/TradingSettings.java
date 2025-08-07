package ru.perminov.model;

import lombok.Data;
import jakarta.persistence.*;

@Data
@Entity
@Table(name = "trading_settings")
public class TradingSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "setting_key", unique = true, nullable = false)
    private String key;
    
    @Column(name = "setting_value", nullable = false)
    private String value;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;
    
    @PreUpdate
    @PrePersist
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }
}

