package com.fga.trader.cryptotraderfuturesbackend.strategies;

import com.fga.trader.cryptotraderfuturesbackend.config.RiskProperties;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.ClaudeValidationPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.KlineSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.records.ClaudeVerdict;
import com.fga.trader.cryptotraderfuturesbackend.records.FVGCandidate;
import com.fga.trader.cryptotraderfuturesbackend.records.FVGPosition;
import com.fga.trader.cryptotraderfuturesbackend.records.FVGValidationResult;
import com.fga.trader.cryptotraderfuturesbackend.utils.RiskCalculator;
import com.fga.tradermodel.dto.KlineDto;
import com.fga.tradermodel.dto.Trend;
import lombok.extern.log4j.Log4j2;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Log4j2
public class FairValueGapStrategy {

    private final KlineSpyPort klineSpyPort;
    private final ClaudeValidationPort claudeValidationPort;
    private final RiskProperties riskProperties;
    private final String symbol;
    private final String temporality;
    private final Trend trend;

    // --- PARÁMETROS DE ESTRATEGIA (FLEXIBILIZADOS) ---
    private static final double MIN_GAP_RATIO = 0.15;
    private static final double MOMENTUM_MULTIPLIER = 1.1;
    private static final double MAX_PENETRATION_RATIO = 0.30;
    private static final double MAX_LIMIT_DISTANCE_RATIO = 0.05;
    private static final double FALLBACK_LIMIT_OFFSET = 0.002;
    private static final double VOLUMEN_THRESHOLD = 1.05;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("America/Mexico_City"));

    public FairValueGapStrategy(KlineSpyPort klineSpyPort, ClaudeValidationPort claudeValidationPort,
                                RiskProperties riskProperties, String symbol, String temporality, Trend trend) {
        this.klineSpyPort = klineSpyPort;
        this.claudeValidationPort = claudeValidationPort;
        this.riskProperties = riskProperties;
        this.symbol = symbol;
        this.temporality = temporality;
        this.trend = trend;
    }

    /**
     * Detecta el patrón localmente, pide a Claude que confirme si es válido o ruido,
     * y si es válido calcula el riesgo (apalancamiento, notional, TP, liquidación) de forma
     * determinística. Devuelve null si no hay patrón o si Claude lo descarta.
     */
    public FVGValidationResult isPatternPresent() {
        List<KlineDto> klines = getKlinesByTemporality();

        if (klines == null || klines.size() < 25) return null;

        int size = klines.size();
        int c1Index = size - 3;
        KlineDto c1 = klines.get(c1Index);
        KlineDto c2 = klines.get(size - 2);
        KlineDto c3 = klines.get(size - 1);

        // 1. FILTRO DE VOLUMEN (Calidad)
        double avgVolume = getAverageVolume(klines, c1Index);
        double requiredVolume = avgVolume * VOLUMEN_THRESHOLD;
        if (c2.getVolume() < requiredVolume) return null;

        // 2. FILTRO DE MOMENTUM
        double bodyC1 = Math.abs(c1.getClosePrice() - c1.getOpenPrice());
        double bodyC2 = Math.abs(c2.getClosePrice() - c2.getOpenPrice());
        double bodyC3 = Math.abs(c3.getClosePrice() - c3.getOpenPrice());

        if (bodyC2 < (bodyC1 * MOMENTUM_MULTIPLIER) || bodyC2 < (bodyC3 * MOMENTUM_MULTIPLIER)) return null;

        double minRequiredGap = bodyC2 * MIN_GAP_RATIO;

        FVGPosition localPosition = (trend == Trend.ALCISTA)
                ? isBullishFvg(klines, c1Index, c1, c2, c3, minRequiredGap, bodyC2)
                : isBearishFvg(klines, c1Index, c1, c2, c3, minRequiredGap, bodyC2);

        if (localPosition == null) return null;

        // 3. CONFIRMACIÓN CUALITATIVA CON CLAUDE
        double gapSize = (trend == Trend.ALCISTA)
                ? c3.getMinPrice() - c1.getMaxPrice()
                : c1.getMinPrice() - c3.getMaxPrice();

        log.info("🤖 [FVG] Patrón {} local detectado en {} en temporalidad {}. Consultando a Claude para validación... (gap={})",
                trend, symbol, temporality, gapSize);

        FVGCandidate candidate = new FVGCandidate(
                symbol, temporality, trend,
                c1.getOpenPrice(), c1.getClosePrice(), c1.getMaxPrice(), c1.getMinPrice(), c1.getVolume(),
                c2.getOpenPrice(), c2.getClosePrice(), c2.getMaxPrice(), c2.getMinPrice(), c2.getVolume(),
                c3.getOpenPrice(), c3.getClosePrice(), c3.getMaxPrice(), c3.getMinPrice(), c3.getVolume(),
                avgVolume, gapSize, localPosition.positionLimit(), riskProperties.getAmountUsdt()
        );

        long claudeStart = System.currentTimeMillis();
        ClaudeVerdict verdict = claudeValidationPort.validateFvg(candidate);
        long claudeElapsed = System.currentTimeMillis() - claudeStart;

        if (verdict == null) {
            log.error("❌ [FVG] Claude devolvió un veredicto NULL para {}. Se descarta por seguridad.", symbol);
            return null;
        }

        log.info("🤖 [FVG] Veredicto de Claude para {}: en temporalidad de {} | C1={} | C3={} | válido={} | clasificación={} | confianza={} | tiempo={} ms | razón={}",
                symbol, temporality,
                TIME_FORMATTER.format(Instant.ofEpochMilli(c1.getOpenTime())),
                TIME_FORMATTER.format(Instant.ofEpochMilli(c3.getOpenTime())),
                verdict.isValid(), verdict.classification(), verdict.confidence(),
                claudeElapsed, verdict.reasoning());

        if (!verdict.isValid()) {
            log.info("🚫 [FVG] {} en {} con temporalidad de {} descartado por Claude (RUIDO). Razón: {}",
                    trend, symbol, temporality, verdict.reasoning());
            return null;
        }

        // 4. CÁLCULO DETERMINÍSTICO DE RIESGO (40 USDT = margen/inversión total)
        double entryPrice = c3.getClosePrice();
        double stopLoss = localPosition.positionLimit();

        RiskCalculator.RiskParams risk;
        try {
            risk = RiskCalculator.calculate(
                    symbol,
                    entryPrice, stopLoss, trend,
                    riskProperties.getAmountUsdt(),
                    riskProperties.getLiquidationBuffer(),
                    riskProperties.getMaxLeverage(),
                    riskProperties.getRiskRewardRatio(),
                    riskProperties.getMaintenanceMargin()
            );
        } catch (Exception e) {
            log.error("❌ [FVG] Error calculando riesgo para {} (entry={}, SL={}): {}",
                    symbol, entryPrice, stopLoss, e.getMessage(), e);
            return null;
        }

        return new FVGValidationResult(
                true, verdict.classification(), verdict.confidence(), trend,
                risk.entryPrice(), risk.stopLoss(), risk.takeProfit(), risk.leverage(),
                risk.marginUsdt(), risk.notionalUsdt(), risk.maxLossUsdt(),
                risk.liquidationPrice(), verdict.reasoning()
        );
    }

    private FVGPosition isBullishFvg(List<KlineDto> klines, int c1Index, KlineDto c1, KlineDto c2, KlineDto c3, double minRequiredGap, double bodyC2) {
        if (c2.getClosePrice() <= c2.getOpenPrice()) return null;

        if (c3.getClosePrice() < c3.getOpenPrice()) {
            if ((c2.getClosePrice() - c3.getClosePrice()) > (bodyC2 * MAX_PENETRATION_RATIO)) return null;
        }

        double gapSize = c3.getMinPrice() - c1.getMaxPrice();
        if (gapSize < minRequiredGap) return null;

        double rawLimit = findSwingLow(klines, c1Index);
        double distanceRatio = (c3.getClosePrice() - rawLimit) / c3.getClosePrice();
        double finalLimit = (distanceRatio > MAX_LIMIT_DISTANCE_RATIO) ? c1.getMinPrice() * (1.0 - FALLBACK_LIMIT_OFFSET) : rawLimit;

        boolean isBreakaway = c3.getClosePrice() > c2.getMaxPrice();
        return new FVGPosition(symbol, finalLimit, isBreakaway ? "BREAKAWAY_GAP" : "STANDARD_GAP", trend);
    }

    private FVGPosition isBearishFvg(List<KlineDto> klines, int c1Index, KlineDto c1, KlineDto c2, KlineDto c3, double minRequiredGap, double bodyC2) {
        if (c2.getClosePrice() >= c2.getOpenPrice()) return null;

        if (c3.getClosePrice() > c3.getOpenPrice()) {
            if ((c3.getClosePrice() - c2.getClosePrice()) > (bodyC2 * MAX_PENETRATION_RATIO)) return null;
        }

        double gapSize = c1.getMinPrice() - c3.getMaxPrice();
        if (gapSize < minRequiredGap) return null;

        double rawLimit = findSwingHigh(klines, c1Index);
        double distanceRatio = (rawLimit - c3.getClosePrice()) / c3.getClosePrice();
        double finalLimit = (distanceRatio > MAX_LIMIT_DISTANCE_RATIO) ? c1.getMaxPrice() * (1.0 + FALLBACK_LIMIT_OFFSET) : rawLimit;

        boolean isBreakaway = c3.getClosePrice() < c2.getMinPrice();
        return new FVGPosition(symbol, finalLimit, isBreakaway ? "BREAKAWAY_GAP" : "STANDARD_GAP", trend);
    }

    private double getAverageVolume(List<KlineDto> klines, int startIndex) {
        double sum = 0;
        int count = 0;
        for (int i = startIndex; i > startIndex - 20 && i >= 0; i--) {
            sum += klines.get(i).getVolume();
            count++;
        }
        if (count == 0) return 0;
        return sum / count;
    }

    private Double findSwingLow(List<KlineDto> klines, int c1Index) {
        double lowest = klines.get(c1Index).getMinPrice();
        for (int i = c1Index - 1; i >= 0; i--) {
            if (klines.get(i).getMinPrice() <= lowest) lowest = klines.get(i).getMinPrice();
            else break;
        }
        return lowest;
    }

    private Double findSwingHigh(List<KlineDto> klines, int c1Index) {
        double highest = klines.get(c1Index).getMaxPrice();
        for (int i = c1Index - 1; i >= 0; i--) {
            if (klines.get(i).getMaxPrice() >= highest) highest = klines.get(i).getMaxPrice();
            else break;
        }
        return highest;
    }

    private List<KlineDto> getKlinesByTemporality() {
        return switch (temporality) {
            case "1d" -> klineSpyPort.getAllBySymbol1d(symbol);
            case "4h" -> klineSpyPort.getAllBySymbol4h(symbol);
            case "1h" -> klineSpyPort.getAllBySymbol1h(symbol);
            case "15m" -> klineSpyPort.getAllBySymbol15m(symbol);
            case "5m" -> klineSpyPort.getAllBySymbol5m(symbol);
            default -> List.of();
        };
    }
}