package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.perminov.model.RiskRule;
import ru.perminov.repository.RiskRuleRepository;
import ru.perminov.repository.TradingSettingsRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RiskRuleService {
    private final RiskRuleRepository repository;
    private final TradingSettingsRepository settingsRepository;

    public Optional<RiskRule> findByFigi(String figi) {
        return repository.findByFigi(figi);
    }

    public RiskRule upsert(String figi, Double sl, Double tp, Boolean active) {
        RiskRule rule = repository.findByFigi(figi).orElseGet(RiskRule::new);
        rule.setFigi(figi);
        if (sl != null) rule.setStopLossPct(sl);
        if (tp != null) rule.setTakeProfitPct(tp);
        if (active != null) rule.setActive(active);
        return repository.save(rule);
    }

    public double getDefaultStopLossPct() {
        return settingsRepository.findByKey("risk_default_sl_pct").map(s -> Double.parseDouble(s.getValue())).orElse(0.05);
    }

    public double getDefaultTakeProfitPct() {
        return settingsRepository.findByKey("risk_default_tp_pct").map(s -> Double.parseDouble(s.getValue())).orElse(0.10);
    }

    public double getRiskPerTradePct() {
        return settingsRepository.findByKey("risk_per_trade_pct").map(s -> Double.parseDouble(s.getValue())).orElse(0.01);
    }
}


