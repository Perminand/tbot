package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.perminov.model.RiskRule;
import ru.perminov.service.RiskRuleService;

import java.util.Map;

@RestController
@RequestMapping("/api/risk-rules")
@RequiredArgsConstructor
public class RiskRuleController {

    private final RiskRuleService riskRuleService;

    @PostMapping
    public ResponseEntity<RiskRule> upsert(@RequestParam String figi,
                                           @RequestParam(required = false) Double stopLossPct,
                                           @RequestParam(required = false) Double takeProfitPct,
                                           @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(riskRuleService.upsert(figi, stopLossPct, takeProfitPct, active));
    }

    @GetMapping("/{figi}")
    public ResponseEntity<?> get(@PathVariable String figi) {
        return riskRuleService.findByFigi(figi)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of("figi", figi, "rule", null)));
    }
}


