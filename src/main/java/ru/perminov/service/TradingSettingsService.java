package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.perminov.model.TradingSettings;
import ru.perminov.repository.TradingSettingsRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingSettingsService {

    private final TradingSettingsRepository repository;

    public String getString(String key, String defaultValue) {
        Optional<TradingSettings> opt = repository.findByKey(key);
        if (opt.isPresent()) {
            TradingSettings setting = opt.get();
            String value = setting.getValue();
            log.info("getString: key={}, found in DB: id={}, value={}, valueLength={}, defaultValue={}", 
                key, setting.getId(), value, value != null ? value.length() : 0, defaultValue);
            if (value == null || value.trim().isEmpty()) {
                log.warn("⚠️ getString: key={} has null or empty value, returning defaultValue={}", key, defaultValue);
                return defaultValue;
            }
            return value;
        } else {
            log.info("getString: key={}, NOT FOUND in DB, using defaultValue={}", key, defaultValue);
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        try {
            return repository.findByKey(key).map(TradingSettings::getValue).map(Double::parseDouble).orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        try {
            return repository.findByKey(key).map(TradingSettings::getValue).map(Integer::parseInt).orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        try {
            return repository.findByKey(key).map(TradingSettings::getValue).map(Boolean::parseBoolean).orElse(defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Transactional
    public void upsert(String key, String value, String description) {
        Optional<TradingSettings> opt = repository.findByKey(key);
        TradingSettings s = opt.orElseGet(TradingSettings::new);
        String oldValue = s.getValue();
        String newValue = value != null ? value.trim() : "";
        s.setKey(key);
        s.setValue(newValue);
        s.setDescription(description);
        TradingSettings saved = repository.saveAndFlush(s); // Используем saveAndFlush для немедленного сохранения
        log.info("upsert: key={}, oldValue={}, newValue={}, savedId={}, savedValue={}, savedValueLength={}", 
            key, oldValue, newValue, saved.getId(), saved.getValue(), saved.getValue() != null ? saved.getValue().length() : 0);
        
        // Дополнительная проверка: читаем обратно из БД
        Optional<TradingSettings> verify = repository.findByKey(key);
        if (verify.isPresent()) {
            String verifiedValue = verify.get().getValue();
            log.info("upsert VERIFY: key={}, verifiedValue={}, matches={}", key, verifiedValue, verifiedValue.equals(newValue));
            if (!verifiedValue.equals(newValue)) {
                log.error("❌ CRITICAL: upsert verification failed! key={}, expected={}, actual={}", key, newValue, verifiedValue);
            }
        } else {
            log.error("❌ CRITICAL: upsert verification failed! key={} not found after save!", key);
        }
    }
}


