package ru.perminov.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.perminov.model.PositionRiskState;
import ru.perminov.model.RiskEvent;
import ru.perminov.service.PositionRiskStateService;
import ru.perminov.service.InstrumentNameService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
@Slf4j
public class RiskController {
    
    private final PositionRiskStateService positionRiskStateService;
    private final InstrumentNameService instrumentNameService;
    
    @GetMapping("/states/{accountId}")
    public List<PositionRiskStateDto> getRiskStates(@PathVariable String accountId) {
        List<PositionRiskState> states = positionRiskStateService.getActiveRiskStates(accountId);
        return states.stream()
            .map(this::enrichWithInstrumentNames)
            .collect(Collectors.toList());
    }
    
    @GetMapping("/states/{accountId}/{figi}")
    public List<PositionRiskStateDto> getRiskStatesByFigi(@PathVariable String accountId, @PathVariable String figi) {
        List<PositionRiskState> states = positionRiskStateService.getRiskState(accountId, figi, null)
            .map(List::of)
            .orElse(List.of());
        return states.stream()
            .map(this::enrichWithInstrumentNames)
            .collect(Collectors.toList());
    }
    
    @GetMapping("/events/{accountId}")
    public List<RiskEventDto> getRiskEvents(@PathVariable String accountId,
                                          @RequestParam(required = false) String figi,
                                          @RequestParam(required = false) String eventType,
                                          @RequestParam(defaultValue = "100") int limit) {
        List<RiskEvent> events;
        if (figi != null) {
            events = positionRiskStateService.getRiskEventsByPosition(accountId, figi, LocalDateTime.now().minusDays(7));
        } else {
            events = positionRiskStateService.getRecentRiskEvents(LocalDateTime.now().minusDays(7));
        }
        
        if (eventType != null) {
            events = events.stream()
                .filter(e -> e.getEventType().name().equals(eventType))
                .collect(Collectors.toList());
        }
        
        return events.stream()
            .limit(limit)
            .map(this::enrichEventWithInstrumentNames)
            .collect(Collectors.toList());
    }
    
    @GetMapping("/active-stops")
    public List<PositionRiskStateDto> getActiveStopLosses() {
        List<PositionRiskState> states = positionRiskStateService.getPositionsWithActiveStopLoss();
        return states.stream()
            .map(this::enrichWithInstrumentNames)
            .collect(Collectors.toList());
    }
    
    @GetMapping("/active-takes")
    public List<PositionRiskStateDto> getActiveTakeProfits() {
        List<PositionRiskState> states = positionRiskStateService.getPositionsWithActiveTakeProfit();
        return states.stream()
            .map(this::enrichWithInstrumentNames)
            .collect(Collectors.toList());
    }
    
    private PositionRiskStateDto enrichWithInstrumentNames(PositionRiskState state) {
        String ticker = instrumentNameService.getTicker(state.getFigi(), "share"); // По умолчанию share
        String name = instrumentNameService.getInstrumentName(state.getFigi(), "share"); // По умолчанию share
        
        return PositionRiskStateDto.builder()
            .id(state.getId())
            .accountId(state.getAccountId())
            .figi(state.getFigi())
            .ticker(ticker)
            .name(name)
            .side(state.getSide())
            .stopLossPct(state.getStopLossPct())
            .takeProfitPct(state.getTakeProfitPct())
            .trailingPct(state.getTrailingPct())
            .stopLossLevel(state.getStopLossLevel())
            .takeProfitLevel(state.getTakeProfitLevel())
            .highWatermark(state.getHighWatermark())
            .lowWatermark(state.getLowWatermark())
            .entryPrice(state.getEntryPrice())
            .averagePriceSnapshot(state.getAveragePriceSnapshot())
            .quantitySnapshot(state.getQuantitySnapshot())
            .trailingType(state.getTrailingType())
            .minStepTicks(state.getMinStepTicks())
            .updatedAt(state.getUpdatedAt())
            .source(state.getSource())
            .build();
    }
    
    private RiskEventDto enrichEventWithInstrumentNames(RiskEvent event) {
        String ticker = instrumentNameService.getTicker(event.getFigi(), "share"); // По умолчанию share
        String name = instrumentNameService.getInstrumentName(event.getFigi(), "share"); // По умолчанию share
        
        return RiskEventDto.builder()
            .id(event.getId())
            .accountId(event.getAccountId())
            .figi(event.getFigi())
            .ticker(ticker)
            .name(name)
            .eventType(event.getEventType())
            .side(event.getSide())
            .oldValue(event.getOldValue())
            .newValue(event.getNewValue())
            .currentPrice(event.getCurrentPrice())
            .watermark(event.getWatermark())
            .reason(event.getReason())
            .details(event.getDetails())
            .createdAt(event.getCreatedAt())
            .build();
    }
    
    // DTO классы
    public static class PositionRiskStateDto {
        private Long id;
        private String accountId;
        private String figi;
        private String ticker;
        private String name;
        private PositionRiskState.PositionSide side;
        private java.math.BigDecimal stopLossPct;
        private java.math.BigDecimal takeProfitPct;
        private java.math.BigDecimal trailingPct;
        private java.math.BigDecimal stopLossLevel;
        private java.math.BigDecimal takeProfitLevel;
        private java.math.BigDecimal highWatermark;
        private java.math.BigDecimal lowWatermark;
        private java.math.BigDecimal entryPrice;
        private java.math.BigDecimal averagePriceSnapshot;
        private java.math.BigDecimal quantitySnapshot;
        private PositionRiskState.TrailingType trailingType;
        private java.math.BigDecimal minStepTicks;
        private LocalDateTime updatedAt;
        private String source;
        
        // Геттеры и сеттеры
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public String getFigi() { return figi; }
        public void setFigi(String figi) { this.figi = figi; }
        
        public String getTicker() { return ticker; }
        public void setTicker(String ticker) { this.ticker = ticker; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public PositionRiskState.PositionSide getSide() { return side; }
        public void setSide(PositionRiskState.PositionSide side) { this.side = side; }
        
        public java.math.BigDecimal getStopLossPct() { return stopLossPct; }
        public void setStopLossPct(java.math.BigDecimal stopLossPct) { this.stopLossPct = stopLossPct; }
        
        public java.math.BigDecimal getTakeProfitPct() { return takeProfitPct; }
        public void setTakeProfitPct(java.math.BigDecimal takeProfitPct) { this.takeProfitPct = takeProfitPct; }
        
        public java.math.BigDecimal getTrailingPct() { return trailingPct; }
        public void setTrailingPct(java.math.BigDecimal trailingPct) { this.trailingPct = trailingPct; }
        
        public java.math.BigDecimal getStopLossLevel() { return stopLossLevel; }
        public void setStopLossLevel(java.math.BigDecimal stopLossLevel) { this.stopLossLevel = stopLossLevel; }
        
        public java.math.BigDecimal getTakeProfitLevel() { return takeProfitLevel; }
        public void setTakeProfitLevel(java.math.BigDecimal takeProfitLevel) { this.takeProfitLevel = takeProfitLevel; }
        
        public java.math.BigDecimal getHighWatermark() { return highWatermark; }
        public void setHighWatermark(java.math.BigDecimal highWatermark) { this.highWatermark = highWatermark; }
        
        public java.math.BigDecimal getLowWatermark() { return lowWatermark; }
        public void setLowWatermark(java.math.BigDecimal lowWatermark) { this.lowWatermark = lowWatermark; }
        
        public java.math.BigDecimal getEntryPrice() { return entryPrice; }
        public void setEntryPrice(java.math.BigDecimal entryPrice) { this.entryPrice = entryPrice; }
        
        public java.math.BigDecimal getAveragePriceSnapshot() { return averagePriceSnapshot; }
        public void setAveragePriceSnapshot(java.math.BigDecimal averagePriceSnapshot) { this.averagePriceSnapshot = averagePriceSnapshot; }
        
        public java.math.BigDecimal getQuantitySnapshot() { return quantitySnapshot; }
        public void setQuantitySnapshot(java.math.BigDecimal quantitySnapshot) { this.quantitySnapshot = quantitySnapshot; }
        
        public PositionRiskState.TrailingType getTrailingType() { return trailingType; }
        public void setTrailingType(PositionRiskState.TrailingType trailingType) { this.trailingType = trailingType; }
        
        public java.math.BigDecimal getMinStepTicks() { return minStepTicks; }
        public void setMinStepTicks(java.math.BigDecimal minStepTicks) { this.minStepTicks = minStepTicks; }
        
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        
        public static PositionRiskStateDtoBuilder builder() {
            return new PositionRiskStateDtoBuilder();
        }
        
        public static class PositionRiskStateDtoBuilder {
            private PositionRiskStateDto dto = new PositionRiskStateDto();
            
            public PositionRiskStateDtoBuilder id(Long id) { dto.id = id; return this; }
            public PositionRiskStateDtoBuilder accountId(String accountId) { dto.accountId = accountId; return this; }
            public PositionRiskStateDtoBuilder figi(String figi) { dto.figi = figi; return this; }
            public PositionRiskStateDtoBuilder ticker(String ticker) { dto.ticker = ticker; return this; }
            public PositionRiskStateDtoBuilder name(String name) { dto.name = name; return this; }
            public PositionRiskStateDtoBuilder side(PositionRiskState.PositionSide side) { dto.side = side; return this; }
            public PositionRiskStateDtoBuilder stopLossPct(java.math.BigDecimal stopLossPct) { dto.stopLossPct = stopLossPct; return this; }
            public PositionRiskStateDtoBuilder takeProfitPct(java.math.BigDecimal takeProfitPct) { dto.takeProfitPct = takeProfitPct; return this; }
            public PositionRiskStateDtoBuilder trailingPct(java.math.BigDecimal trailingPct) { dto.trailingPct = trailingPct; return this; }
            public PositionRiskStateDtoBuilder stopLossLevel(java.math.BigDecimal stopLossLevel) { dto.stopLossLevel = stopLossLevel; return this; }
            public PositionRiskStateDtoBuilder takeProfitLevel(java.math.BigDecimal takeProfitLevel) { dto.takeProfitLevel = takeProfitLevel; return this; }
            public PositionRiskStateDtoBuilder highWatermark(java.math.BigDecimal highWatermark) { dto.highWatermark = highWatermark; return this; }
            public PositionRiskStateDtoBuilder lowWatermark(java.math.BigDecimal lowWatermark) { dto.lowWatermark = lowWatermark; return this; }
            public PositionRiskStateDtoBuilder entryPrice(java.math.BigDecimal entryPrice) { dto.entryPrice = entryPrice; return this; }
            public PositionRiskStateDtoBuilder averagePriceSnapshot(java.math.BigDecimal averagePriceSnapshot) { dto.averagePriceSnapshot = averagePriceSnapshot; return this; }
            public PositionRiskStateDtoBuilder quantitySnapshot(java.math.BigDecimal quantitySnapshot) { dto.quantitySnapshot = quantitySnapshot; return this; }
            public PositionRiskStateDtoBuilder trailingType(PositionRiskState.TrailingType trailingType) { dto.trailingType = trailingType; return this; }
            public PositionRiskStateDtoBuilder minStepTicks(java.math.BigDecimal minStepTicks) { dto.minStepTicks = minStepTicks; return this; }
            public PositionRiskStateDtoBuilder updatedAt(LocalDateTime updatedAt) { dto.updatedAt = updatedAt; return this; }
            public PositionRiskStateDtoBuilder source(String source) { dto.source = source; return this; }
            
            public PositionRiskStateDto build() { return dto; }
        }
    }
    
    public static class RiskEventDto {
        private Long id;
        private String accountId;
        private String figi;
        private String ticker;
        private String name;
        private RiskEvent.EventType eventType;
        private String side;
        private java.math.BigDecimal oldValue;
        private java.math.BigDecimal newValue;
        private java.math.BigDecimal currentPrice;
        private java.math.BigDecimal watermark;
        private String reason;
        private String details;
        private LocalDateTime createdAt;
        
        // Геттеры и сеттеры
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public String getFigi() { return figi; }
        public void setFigi(String figi) { this.figi = figi; }
        
        public String getTicker() { return ticker; }
        public void setTicker(String ticker) { this.ticker = ticker; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public RiskEvent.EventType getEventType() { return eventType; }
        public void setEventType(RiskEvent.EventType eventType) { this.eventType = eventType; }
        
        public String getSide() { return side; }
        public void setSide(String side) { this.side = side; }
        
        public java.math.BigDecimal getOldValue() { return oldValue; }
        public void setOldValue(java.math.BigDecimal oldValue) { this.oldValue = oldValue; }
        
        public java.math.BigDecimal getNewValue() { return newValue; }
        public void setNewValue(java.math.BigDecimal newValue) { this.newValue = newValue; }
        
        public java.math.BigDecimal getCurrentPrice() { return currentPrice; }
        public void setCurrentPrice(java.math.BigDecimal currentPrice) { this.currentPrice = currentPrice; }
        
        public java.math.BigDecimal getWatermark() { return watermark; }
        public void setWatermark(java.math.BigDecimal watermark) { this.watermark = watermark; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
        
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        
        public static RiskEventDtoBuilder builder() {
            return new RiskEventDtoBuilder();
        }
        
        public static class RiskEventDtoBuilder {
            private RiskEventDto dto = new RiskEventDto();
            
            public RiskEventDtoBuilder id(Long id) { dto.id = id; return this; }
            public RiskEventDtoBuilder accountId(String accountId) { dto.accountId = accountId; return this; }
            public RiskEventDtoBuilder figi(String figi) { dto.figi = figi; return this; }
            public RiskEventDtoBuilder ticker(String ticker) { dto.ticker = ticker; return this; }
            public RiskEventDtoBuilder name(String name) { dto.name = name; return this; }
            public RiskEventDtoBuilder eventType(RiskEvent.EventType eventType) { dto.eventType = eventType; return this; }
            public RiskEventDtoBuilder side(String side) { dto.side = side; return this; }
            public RiskEventDtoBuilder oldValue(java.math.BigDecimal oldValue) { dto.oldValue = oldValue; return this; }
            public RiskEventDtoBuilder newValue(java.math.BigDecimal newValue) { dto.newValue = newValue; return this; }
            public RiskEventDtoBuilder currentPrice(java.math.BigDecimal currentPrice) { dto.currentPrice = currentPrice; return this; }
            public RiskEventDtoBuilder watermark(java.math.BigDecimal watermark) { dto.watermark = watermark; return this; }
            public RiskEventDtoBuilder reason(String reason) { dto.reason = reason; return this; }
            public RiskEventDtoBuilder details(String details) { dto.details = details; return this; }
            public RiskEventDtoBuilder createdAt(LocalDateTime createdAt) { dto.createdAt = createdAt; return this; }
            
            public RiskEventDto build() { return dto; }
        }
    }
}
