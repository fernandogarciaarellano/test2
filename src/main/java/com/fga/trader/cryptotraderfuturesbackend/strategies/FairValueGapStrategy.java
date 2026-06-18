package com.fga.trader.cryptotraderfuturesbackend.strategies;

import com.fga.trader.cryptotraderfuturesbackend.config.RiskProperties;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.ClaudeValidationPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.KlineSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.records.ClaudeVerdict;
import com.fga.trader.cryptotraderfuturesbackend.records.FVGCandidate;
import com.fga.trader.cryptotraderfuturesbackend.records.FVGValidationResult;
import com.fga.trader.cryptotraderfuturesbackend.utils.RiskCalculator;
import com.fga.tradermodel.dto.KlineDto;
import com.fga.tradermodel.dto.Trend;
import lombok.extern.log4j.Log4j2;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Log4j2
public class FairValueGapStrategy {

    private final KlineSpyPort klineSpyPort;
    private final ClaudeValidationPort claudeValidationPort;
    private final RiskProperties riskProperties;
    private final String symbol;
    private final String temporality;
    private final Trend trend;

    // --- PARÁMETROS INSTITUCIONALES ---
    private static final double MIN_ATR_GAP_MULTIPLIER = 0.5; // FVG debe ser > 50% del ATR
    private static final double MIN_RVOL = 1.5;               // Opcional: usar para filtros estrictos adicionales
    private static final int SWING_LOOKBACK = 50;             // Velas para medir Premium/Discount
    private static final int LIQUIDITY_SWEEP_LOOKBACK = 20;   // Velas para buscar barridos (Stop Hunts)

    public FairValueGapStrategy(KlineSpyPort klineSpyPort, ClaudeValidationPort claudeValidationPort,
                                RiskProperties riskProperties, String symbol, String temporality, Trend trend) {
        this.klineSpyPort = klineSpyPort;
        this.claudeValidationPort = claudeValidationPort;
        this.riskProperties = riskProperties;
        this.symbol = symbol;
        this.temporality = temporality;
        this.trend = trend;
    }

    public FVGValidationResult isPatternPresent() {
        List<KlineDto> klines = getKlinesByTemporality();

        // Necesitamos suficientes datos para cálculos pesados (SMA, ATR, Swings)
        if (klines == null || klines.size() < SWING_LOOKBACK + 5) {
            log.debug("No hay suficientes velas para {} en {}", symbol, temporality);
            return null;
        }

        int lastIndex = klines.size() - 1;
        KlineDto c1 = klines.get(lastIndex - 2);
        KlineDto c2 = klines.get(lastIndex - 1);
        KlineDto c3 = klines.get(lastIndex);

        boolean isLong = (trend == Trend.ALCISTA);
        double gapSize = 0.0;
        double proposedLimit = 0.0;

        // 1. Detección Estructural Básica del Gap
        if (isLong) {
            if (c3.getMinPrice() > c1.getMaxPrice() && c2.getClosePrice() > c2.getOpenPrice()) {
                gapSize = c3.getMinPrice() - c1.getMaxPrice();
                proposedLimit = c1.getMinPrice(); // Invalida si pierde la vela base
            } else return null;
        } else {
            if (c3.getMaxPrice() < c1.getMinPrice() && c2.getClosePrice() < c2.getOpenPrice()) {
                gapSize = c1.getMinPrice() - c3.getMaxPrice();
                proposedLimit = c1.getMaxPrice(); // Invalida si supera la vela base
            } else return null;
        }

        // 2. Cálculos Cuantitativos y de Contexto SMC
        double atr = calculateATR(klines, lastIndex, 14);
        double rvol = calculateRVOL(klines, lastIndex - 1, 20); // Volumen de C2 vs Promedio de 20
        boolean inDiscountZone = evaluatePremiumDiscount(klines, lastIndex, c3.getClosePrice(), isLong);
        boolean didSweepLiquidity = evaluateLiquiditySweep(klines, lastIndex - 2, isLong);
        boolean isAlignedWithVWAP = evaluateVWAP(klines, lastIndex, c3.getClosePrice(), isLong);
        boolean isInKillzone = evaluateKillzone(c3.getCloseTime());

        double gapVsAtr = gapSize / atr;

        // 3. Filtros Duros (Optimizando llamadas a la IA)
        if (gapVsAtr < MIN_ATR_GAP_MULTIPLIER) {
            log.debug("❌ [Filtro] FVG muy pequeño vs ATR. Gap: {}, ATR: {}", String.format("%.6f", gapSize), String.format("%.6f", atr));
            return null;
        }
        if (!isAlignedWithVWAP) {
            log.debug("❌ [Filtro] FVG formado en contra del VWAP direccional. Descartado.");
            return null;
        }

        // 4. Armar el candidato con contexto enriquecido para Claude
        FVGCandidate candidate = new FVGCandidate(
                symbol, temporality, trend,
                c1.getOpenPrice(), c1.getClosePrice(), c1.getMaxPrice(), c1.getMinPrice(), c1.getVolume(),
                c2.getOpenPrice(), c2.getClosePrice(), c2.getMaxPrice(), c2.getMinPrice(), c2.getVolume(),
                c3.getOpenPrice(), c3.getClosePrice(), c3.getMaxPrice(), c3.getMinPrice(), c3.getVolume(),
                gapSize, proposedLimit, riskProperties.getAmountUsdt(),
                inDiscountZone, didSweepLiquidity, isAlignedWithVWAP, gapVsAtr, rvol, isInKillzone
        );

        // 5. Validación final a través de Claude AI
        ClaudeVerdict verdict = claudeValidationPort.validateFvg(candidate);

        if (verdict != null && verdict.isValid() && verdict.confidence() > 0.70) {
            double entryPrice = c3.getClosePrice();

            // Cálculo dinámico de Riesgo, Apalancamiento y Notional
            RiskCalculator.RiskParams rp = RiskCalculator.calculate(
                    symbol, entryPrice, proposedLimit, trend,
                    riskProperties.getAmountUsdt(),
                    riskProperties.getLiquidationBuffer(),
                    riskProperties.getMaxLeverage(),
                    riskProperties.getRiskRewardRatio(),
                    riskProperties.getMaintenanceMargin()
            );

            return new FVGValidationResult(
                    true, verdict.classification(), verdict.confidence(), trend,
                    entryPrice, proposedLimit, rp.takeProfit(), rp.leverage(),
                    rp.marginUsdt(), rp.notionalUsdt(), rp.maxLossUsdt(), rp.liquidationPrice(),
                    verdict.reasoning()
            );
        }

        return null;
    }

    // =========================================================================
    // MÉTODOS MATEMÁTICOS INSTITUCIONALES (SMC & QUANT)
    // =========================================================================

    private double calculateATR(List<KlineDto> klines, int currentIndex, int period) {
        double atr = 0;
        for (int i = currentIndex - period + 1; i <= currentIndex; i++) {
            KlineDto current = klines.get(i);
            KlineDto prev = klines.get(i - 1);
            double highLow = current.getMaxPrice() - current.getMinPrice();
            double highClose = Math.abs(current.getMaxPrice() - prev.getClosePrice());
            double lowClose = Math.abs(current.getMinPrice() - prev.getClosePrice());
            double trueRange = Math.max(highLow, Math.max(highClose, lowClose));
            atr += trueRange;
        }
        return atr / period;
    }

    private double calculateRVOL(List<KlineDto> klines, int targetIndex, int period) {
        double sumVol = 0;
        int count = 0;
        for (int i = targetIndex - period; i < targetIndex; i++) {
            sumVol += klines.get(i).getVolume();
            count++;
        }
        double avgVol = (count > 0) ? sumVol / count : 1;
        return klines.get(targetIndex).getVolume() / avgVol;
    }

    private boolean evaluatePremiumDiscount(List<KlineDto> klines, int currentIndex, double currentPrice, boolean isLong) {
        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;
        for (int i = currentIndex - SWING_LOOKBACK; i <= currentIndex; i++) {
            if (klines.get(i).getMaxPrice() > highest) highest = klines.get(i).getMaxPrice();
            if (klines.get(i).getMinPrice() < lowest) lowest = klines.get(i).getMinPrice();
        }
        double equilibrium = (highest + lowest) / 2.0;
        return isLong ? (currentPrice < equilibrium) : (currentPrice > equilibrium);
    }

    private boolean evaluateLiquiditySweep(List<KlineDto> klines, int c1Index, boolean isLong) {
        double reference = isLong ? klines.get(c1Index).getMinPrice() : klines.get(c1Index).getMaxPrice();
        for (int i = c1Index - LIQUIDITY_SWEEP_LOOKBACK; i < c1Index; i++) {
            if (isLong && klines.get(i).getMinPrice() < reference) return false;
            if (!isLong && klines.get(i).getMaxPrice() > reference) return false;
        }
        return true;
    }

    private boolean evaluateVWAP(List<KlineDto> klines, int currentIndex, double currentPrice, boolean isLong) {
        double cumulativeTypicalPriceVolume = 0;
        double cumulativeVolume = 0;
        int periods = Math.min(klines.size(), 24);

        for (int i = currentIndex - periods + 1; i <= currentIndex; i++) {
            KlineDto k = klines.get(i);
            double typicalPrice = (k.getMaxPrice() + k.getMinPrice() + k.getClosePrice()) / 3;
            cumulativeTypicalPriceVolume += typicalPrice * k.getVolume();
            cumulativeVolume += k.getVolume();
        }
        double vwap = cumulativeVolume > 0 ? cumulativeTypicalPriceVolume / cumulativeVolume : currentPrice;
        return isLong ? (currentPrice > vwap) : (currentPrice < vwap);
    }

    private boolean evaluateKillzone(long timestamp) {
        // Killzone: Solapamiento London-NY (Aprox 12:00 UTC a 16:00 UTC)
        ZonedDateTime time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("UTC"));
        int hour = time.getHour();
        return (hour >= 12 && hour <= 16);
    }

    private List<KlineDto> getKlinesByTemporality() {
        return switch (temporality) {
            case "1d" -> klineSpyPort.getAllBySymbol1d(symbol);
            case "4h" -> klineSpyPort.getAllBySymbol4h(symbol);
            case "1h" -> klineSpyPort.getAllBySymbol1h(symbol);
            case "15m" -> klineSpyPort.getAllBySymbol15m(symbol);
            case "5m" -> klineSpyPort.getAllBySymbol5m(symbol);
            default -> null;
        };
    }
}