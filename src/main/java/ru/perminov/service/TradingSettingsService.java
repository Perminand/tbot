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
        try {
            Optional<TradingSettings> opt = repository.findByKey(key);
            if (opt.isPresent()) {
                TradingSettings setting = opt.get();
                String value = setting.getValue();
                log.info("getString: key={}, found in DB: id={}, value='{}', valueLength={}, valueIsNull={}, valueIsEmpty={}, defaultValue='{}'", 
                    key, setting.getId(), value, value != null ? value.length() : 0, 
                    value == null, value != null && value.trim().isEmpty(), defaultValue);
                
                // Возвращаем значение как есть, даже если оно пустое (это может быть валидное значение "false")
                // Только если значение null, возвращаем defaultValue
                if (value == null) {
                    log.warn("⚠️ getString: key={} has NULL value in DB, returning defaultValue='{}'", key, defaultValue);
                    return defaultValue;
                }
                
                // Возвращаем значение как есть (даже пустую строку - это валидное значение)
                return value;
            } else {
                log.info("getString: key={}, NOT FOUND in DB, using defaultValue='{}'", key, defaultValue);
                return defaultValue;
            }
        } catch (Exception e) {
            log.error("❌ getString ERROR: key={}, error={}", key, e.getMessage(), e);
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

    /**
     * Получение настройки напрямую из репозитория (для отладки)
     */
    public Optional<TradingSettings> getSetting(String key) {
        return repository.findByKey(key);
    }

    @Transactional
    public void upsert(String key, String value, String description) {
        try {
            Optional<TradingSettings> opt = repository.findByKey(key);
            TradingSettings s = opt.orElseGet(TradingSettings::new);
            String oldValue = s.getValue();
            String newValue = value != null ? value.trim() : "";
            s.setKey(key);
            s.setValue(newValue);
            s.setDescription(description != null ? description : "");
            TradingSettings saved = repository.saveAndFlush(s); // Используем saveAndFlush для немедленного сохранения
            log.info("upsert SUCCESS: key={}, oldValue={}, newValue={}, savedId={}, savedValue={}, savedValueLength={}", 
                key, oldValue, newValue, saved.getId(), saved.getValue(), saved.getValue() != null ? saved.getValue().length() : 0);
        } catch (Exception e) {
            log.error("❌ upsert ERROR: key={}, value={}, error={}", key, value, e.getMessage(), e);
            e.printStackTrace();
            throw e; // Пробрасываем исключение дальше
        }
    }
}


