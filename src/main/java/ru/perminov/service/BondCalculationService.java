package ru.perminov.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Coupon;
import ru.tinkoff.piapi.core.InvestApi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BondCalculationService {
    
    private final InvestApiManager investApiManager;
    
    /**
     * Расчет НКД (Накопленного купонного дохода)
     */
    public BigDecimal calculateAccumulatedCouponYield(String figi, BigDecimal currentPrice) {
        try {
            // Получаем информацию об облигации
            Bond bond = investApiManager.getCurrentInvestApi().getInstrumentsService().getBondByFigiSync(figi);
            if (bond == null) {
                log.warn("Не удалось получить информацию об облигации");
                return BigDecimal.ZERO;
            }
            
            // Получаем купоны за последний год
            Instant from = Instant.now().minus(365, ChronoUnit.DAYS);
            Instant to = Instant.now().plus(365, ChronoUnit.DAYS);
            List<Coupon> coupons = investApiManager.getCurrentInvestApi().getInstrumentsService().getBondCouponsSync(figi, from, to);
            if (coupons.isEmpty()) {
                log.warn("Не найдены купоны для облигации");
                return BigDecimal.ZERO;
            }
            
            // Находим следующий купон
            LocalDate today = LocalDate.now();
            Coupon nextCoupon = null;
            
            for (Coupon coupon : coupons) {
                LocalDate couponDate = LocalDate.parse(coupon.getCouponDate().toString().split("T")[0]);
                if (couponDate.isAfter(today)) {
                    nextCoupon = coupon;
                    break;
                }
            }
            
            if (nextCoupon == null) {
                log.warn("Не найден следующий купон для облигации");
                return BigDecimal.ZERO;
            }
            
            // Находим предыдущий купон
            Coupon prevCoupon = null;
            for (Coupon coupon : coupons) {
                LocalDate couponDate = LocalDate.parse(coupon.getCouponDate().toString().split("T")[0]);
                if (couponDate.isBefore(today) || couponDate.isEqual(today)) {
                    prevCoupon = coupon;
                }
            }
            
            if (prevCoupon == null) {
                log.warn("Не найден предыдущий купон для облигации");
                return BigDecimal.ZERO;
            }
            
            // Расчет НКД
            LocalDate prevDate = LocalDate.parse(prevCoupon.getCouponDate().toString().split("T")[0]);
            LocalDate nextDate = LocalDate.parse(nextCoupon.getCouponDate().toString().split("T")[0]);
            
            long daysSincePrevCoupon = ChronoUnit.DAYS.between(prevDate, today);
            long daysBetweenCoupons = ChronoUnit.DAYS.between(prevDate, nextDate);
            
            if (daysBetweenCoupons == 0) {
                return BigDecimal.ZERO;
            }
            
            // НКД = (Купон * Дни с последней выплаты) / Дни между купонами
            BigDecimal couponValue = new BigDecimal(prevCoupon.getPayOneBond().getUnits() + "." + 
                String.format("%09d", prevCoupon.getPayOneBond().getNano()));
            
            BigDecimal nkd = couponValue
                .multiply(BigDecimal.valueOf(daysSincePrevCoupon))
                .divide(BigDecimal.valueOf(daysBetweenCoupons), 4, RoundingMode.HALF_UP);
            
            log.debug("НКД: {} (купон: {}, дни: {}/{})", 
                nkd, couponValue, daysSincePrevCoupon, daysBetweenCoupons);
            
            return nkd;
            
        } catch (Exception e) {
            log.error("Ошибка расчета НКД: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Расчет текущей доходности облигации
     */
    public BigDecimal calculateCurrentYield(String figi, BigDecimal currentPrice) {
        try {
            // Получаем информацию об облигации
            Bond bond = investApiManager.getCurrentInvestApi().getInstrumentsService().getBondByFigiSync(figi);
            if (bond == null) {
                log.warn("Не удалось получить информацию об облигации");
                return BigDecimal.ZERO;
            }
            
            // Получаем купоны за последний год
            Instant from = Instant.now().minus(365, ChronoUnit.DAYS);
            Instant to = Instant.now().plus(365, ChronoUnit.DAYS);
            List<Coupon> coupons = investApiManager.getCurrentInvestApi().getInstrumentsService().getBondCouponsSync(figi, from, to);
            if (coupons.isEmpty()) {
                log.warn("Не найдены купоны для облигации");
                return BigDecimal.ZERO;
            }
            
            // Находим годовой купонный доход
            BigDecimal annualCouponIncome = BigDecimal.ZERO;
            int couponCount = 0;
            
            for (Coupon coupon : coupons) {
                LocalDate couponDate = LocalDate.parse(coupon.getCouponDate().toString().split("T")[0]);
                LocalDate today = LocalDate.now();
                
                // Берем купоны за последний год
                if (couponDate.isAfter(today.minusYears(1))) {
                    BigDecimal couponValue = new BigDecimal(coupon.getPayOneBond().getUnits() + "." + 
                        String.format("%09d", coupon.getPayOneBond().getNano()));
                    annualCouponIncome = annualCouponIncome.add(couponValue);
                    couponCount++;
                }
            }
            
            if (couponCount == 0) {
                log.warn("Не найдены купоны за последний год для облигации");
                return BigDecimal.ZERO;
            }
            
            // Усредняем годовой доход
            annualCouponIncome = annualCouponIncome.divide(BigDecimal.valueOf(couponCount), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(12)); // Умножаем на 12 для годового дохода
            
            // Текущая доходность = (Годовой купонный доход / Текущая цена) * 100
            if (currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentYield = annualCouponIncome
                    .divide(currentPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                
                log.debug("Текущая доходность для {}: {}% (годовой купон: {}, цена: {})", 
                    figi, currentYield, annualCouponIncome, currentPrice);
                
                return currentYield;
            }
            
            return BigDecimal.ZERO;
            
        } catch (Exception e) {
            log.error("Ошибка расчета доходности: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Расчет доходности к погашению (YTM) - упрощенная версия
     */
    public BigDecimal calculateYieldToMaturity(String figi, BigDecimal currentPrice, BigDecimal faceValue) {
        try {
            // Получаем информацию об облигации
            Bond bond = investApiManager.getCurrentInvestApi().getInstrumentsService().getBondByFigiSync(figi);
            if (bond == null) {
                log.warn("Не удалось получить информацию об облигации");
                return BigDecimal.ZERO;
            }
            
            // Получаем дату погашения
            String maturityDateStr = bond.getMaturityDate().toString();
            if (maturityDateStr == null || maturityDateStr.isEmpty()) {
                log.warn("Не найдена дата погашения для облигации");
                return BigDecimal.ZERO;
            }
            
            LocalDate maturityDate = LocalDate.parse(maturityDateStr.split("T")[0]);
            LocalDate today = LocalDate.now();
            
            if (maturityDate.isBefore(today)) {
                log.warn("Облигация уже погашена");
                return BigDecimal.ZERO;
            }
            
            // Получаем купоны до погашения
            Instant from = Instant.now();
            Instant to = Instant.now().plus(365 * 10, ChronoUnit.DAYS); // 10 лет вперед
            List<Coupon> coupons = investApiManager.getCurrentInvestApi().getInstrumentsService().getBondCouponsSync(figi, from, to);
            BigDecimal totalCouponIncome = BigDecimal.ZERO;
            
            for (Coupon coupon : coupons) {
                LocalDate couponDate = LocalDate.parse(coupon.getCouponDate().toString().split("T")[0]);
                if (couponDate.isAfter(today) && !couponDate.isAfter(maturityDate)) {
                    BigDecimal couponValue = new BigDecimal(coupon.getPayOneBond().getUnits() + "." + 
                        String.format("%09d", coupon.getPayOneBond().getNano()));
                    totalCouponIncome = totalCouponIncome.add(couponValue);
                }
            }
            
            // Расчет YTM (упрощенная формула)
            long yearsToMaturity = ChronoUnit.DAYS.between(today, maturityDate) / 365;
            if (yearsToMaturity == 0) {
                yearsToMaturity = 1;
            }
            
            // YTM = ((Номинал - Текущая цена + Купонный доход) / Текущая цена) / Лет до погашения * 100
            BigDecimal priceDifference = faceValue.subtract(currentPrice);
            BigDecimal totalReturn = priceDifference.add(totalCouponIncome);
            
            if (currentPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ytm = totalReturn
                    .divide(currentPrice, 4, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(yearsToMaturity), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                
                log.debug("YTM для {}: {}% (номинал: {}, цена: {}, купоны: {}, лет: {})", 
                    figi, ytm, faceValue, currentPrice, totalCouponIncome, yearsToMaturity);
                
                return ytm;
            }
            
            return BigDecimal.ZERO;
            
        } catch (Exception e) {
            log.error("Ошибка расчета YTM: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }
} 