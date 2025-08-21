package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.perminov.model.TradingSettings;
import ru.perminov.repository.TradingSettingsRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TradingSettingsService {

    private final TradingSettingsRepository repository;

    public String getString(String key, String defaultValue) {
        return repository.findByKey(key).map(TradingSettings::getValue).orElse(defaultValue);
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
        s.setKey(key);
        s.setValue(value);
        s.setDescription(description);
        repository.save(s);
    }
}


