package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestService {

    private final MarketAnalysisService marketAnalysisService;
    private final AdvancedTradingStrategyService advancedTradingStrategyService;

    public Result run(String figi, CandleInterval interval, LocalDate fromDate, LocalDate toDate,
                      BigDecimal initialCash, BigDecimal commissionPct, int slippageTicks) {

        Instant from = fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = toDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        List<HistoricCandle> candles = marketAnalysisService.getCandles(figi, interval, from, to);
        if (candles == null || candles.isEmpty()) {
            return new Result(figi, initialCash, initialCash, BigDecimal.ZERO, 0, 0, new ArrayList<>());
        }

        BigDecimal cash = initialCash;
        int positionLots = 0;
        BigDecimal avgPrice = BigDecimal.ZERO;
        BigDecimal equity = initialCash;
        int trades = 0;
        int wins = 0;
        List<Trade> tradeLog = new ArrayList<>();

        for (HistoricCandle c : candles) {
            BigDecimal price = toPrice(c.getClose());

            String action = advancedTradingStrategyService
                    .analyzeTradingSignal(figi, "backtest")
                    .getAction();

            // простая позиционная логика: buy -> открыть/добавить; sell -> закрыть; hold -> ничего
            if ("BUY".equals(action)) {
                if (cash.compareTo(price) >= 0) {
                    int lots = cash.divide(price, 0, RoundingMode.DOWN).intValue();
                    if (lots > 0) {
                        BigDecimal cost = price.multiply(BigDecimal.valueOf(lots));
                        BigDecimal commission = cost.multiply(commissionPct);
                        cash = cash.subtract(cost).subtract(commission);
                        avgPrice = positionLots == 0 ? price :
                                avgPrice.multiply(BigDecimal.valueOf(positionLots))
                                        .add(price.multiply(BigDecimal.valueOf(lots)))
                                        .divide(BigDecimal.valueOf(positionLots + lots), 6, RoundingMode.HALF_UP);
                        positionLots += lots;
                        tradeLog.add(new Trade("BUY", lots, price, commission));
                        trades++;
                    }
                }
            } else if ("SELL".equals(action) && positionLots > 0) {
                int lots = positionLots;
                BigDecimal revenue = price.multiply(BigDecimal.valueOf(lots));
                BigDecimal commission = revenue.multiply(commissionPct);
                cash = cash.add(revenue).subtract(commission);

                BigDecimal pnlPerLot = price.subtract(avgPrice);
                if (pnlPerLot.compareTo(BigDecimal.ZERO) > 0) wins++;

                positionLots = 0;
                avgPrice = BigDecimal.ZERO;
                tradeLog.add(new Trade("SELL", lots, price, commission));
                trades++;
            }

            // Пересчет equity (позиция по текущей цене)
            equity = cash.add(price.multiply(BigDecimal.valueOf(positionLots)));
        }

        BigDecimal profit = equity.subtract(initialCash);
        BigDecimal roi = profit.divide(initialCash, 4, RoundingMode.HALF_UP);

        return new Result(figi, initialCash, equity, roi, trades, wins, tradeLog);
    }

    private BigDecimal toPrice(ru.tinkoff.piapi.contract.v1.Quotation q) {
        if (q == null) return BigDecimal.ZERO;
        String nano = String.format("%09d", q.getNano());
        return new BigDecimal(q.getUnits() + "." + nano);
    }

    public static class Result {
        public final String figi;
        public final BigDecimal startCash;
        public final BigDecimal endEquity;
        public final BigDecimal roi;
        public final int trades;
        public final int wins;
        public final List<Trade> tradesLog;

        public Result(String figi, BigDecimal startCash, BigDecimal endEquity, BigDecimal roi,
                      int trades, int wins, List<Trade> tradesLog) {
            this.figi = figi;
            this.startCash = startCash;
            this.endEquity = endEquity;
            this.roi = roi;
            this.trades = trades;
            this.wins = wins;
            this.tradesLog = tradesLog;
        }
    }

    public static class Trade {
        public final String side;
        public final int lots;
        public final BigDecimal price;
        public final BigDecimal commission;

        public Trade(String side, int lots, BigDecimal price, BigDecimal commission) {
            this.side = side;
            this.lots = lots;
            this.price = price;
            this.commission = commission;
        }
    }
}


