package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
            String value = opt.get().getValue();
            log.debug("getString: key={}, found value={}, defaultValue={}", key, value, defaultValue);
            return value != null ? value : defaultValue;
        } else {
            log.debug("getString: key={}, not found, using defaultValue={}", key, defaultValue);
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

    public void upsert(String key, String value, String description) {
        Optional<TradingSettings> opt = repository.findByKey(key);
        TradingSettings s = opt.orElseGet(TradingSettings::new);
        String oldValue = s.getValue();
        s.setKey(key);
        s.setValue(value != null ? value.trim() : "");
        s.setDescription(description);
        TradingSettings saved = repository.save(s);
        log.info("upsert: key={}, oldValue={}, newValue={}, savedValue={}", 
            key, oldValue, value, saved.getValue());
    }
}


