package com.fga.trader.cryptotraderfuturesbackend.utils;

import com.fga.tradermodel.dto.Trend;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class RiskCalculator {

    public record RiskParams(
            double entryPrice,
            double stopLoss,
            double takeProfit,
            int leverage,
            double marginUsdt,        // tu inversión total (40 USDT)
            double notionalUsdt,      // tamaño de la posición = margen * leverage
            double maxLossUsdt,       // pérdida estimada si toca el SL
            double liquidationPrice
    ) {}

    /**
     * @param entryPrice        precio de entrada (close C3)
     * @param stopLoss          nivel de stop loss propuesto por la estrategia
     * @param trend             ALCISTA (long) o BAJISTA (short)
     * @param marginUsdt        inversión total = margen comprometido (40 USDT)
     * @param liquidationBuffer fracción de seguridad: el SL debe estar a <= buffer de la distancia de liquidación (ej 0.5)
     * @param maxLeverage       apalancamiento máximo permitido por el exchange/config
     * @param riskRewardRatio   ratio R:B para el take profit (ej 2.0)
     * @param maintenanceMargin margen de mantenimiento del exchange (ej 0.005 = 0.5%)
     */
    public static RiskParams calculate(String symbol, double entryPrice, double stopLoss, Trend trend,
                                       double marginUsdt, double liquidationBuffer,
                                       int maxLeverage, double riskRewardRatio,
                                       double maintenanceMargin) {

        // 1. Distancia porcentual al stop loss
        double slDistanceRatio = Math.abs(entryPrice - stopLoss) / entryPrice;
        if (slDistanceRatio <= 0) {
            throw new IllegalArgumentException("Stop loss inválido: distancia cero respecto a la entrada");
        }

        // 2. Apalancamiento máximo para que la LIQUIDACIÓN quede MÁS ALLÁ del SL con buffer.
        //    La liquidación ocurre aprox cuando el precio se mueve (1/leverage - maintenanceMargin).
        //    Queremos: slDistanceRatio <= buffer * distancia_a_liquidacion
        //    => leverage <= buffer / (slDistanceRatio + buffer * maintenanceMargin)
        double maxSafeLeverage = liquidationBuffer / (slDistanceRatio + liquidationBuffer * maintenanceMargin);
        int leverage = (int) Math.floor(maxSafeLeverage);
        if (leverage < 1) leverage = 1;
        if (leverage > maxLeverage) leverage = maxLeverage;

        // 3. Notional: tamaño de la posición = margen * apalancamiento
        double notionalUsdt = marginUsdt * leverage;

        // 4. Pérdida estimada si el precio toca el SL = notional * distancia al SL
        double maxLossUsdt = notionalUsdt * slDistanceRatio;

        // 5. Take profit según ratio R:B
        double slDistanceAbs = Math.abs(entryPrice - stopLoss);
        double tpDistanceAbs = slDistanceAbs * riskRewardRatio;
        double takeProfit = (trend == Trend.ALCISTA)
                ? entryPrice + tpDistanceAbs
                : entryPrice - tpDistanceAbs;

        // 6. Precio de liquidación aproximado (sin comisiones/funding)
        double liqDistanceRatio = (1.0 / leverage) - maintenanceMargin;
        double liquidationPrice = (trend == Trend.ALCISTA)
                ? entryPrice * (1.0 - liqDistanceRatio)
                : entryPrice * (1.0 + liqDistanceRatio);

        log.info("📊 RiskCalc Symbol: {} | SL dist: {}% | Lev: {}x | Margen: {} USDT | Notional: {} USDT | LiqPrice: {} | SL: {} | Pérdida máx si SL: {} USDT",
                symbol,
                String.format("%.3f", slDistanceRatio * 100), leverage,
                String.format("%.2f", marginUsdt), String.format("%.2f", notionalUsdt),
                String.format("%.6f", liquidationPrice), String.format("%.6f", stopLoss),
                String.format("%.2f", maxLossUsdt));

        return new RiskParams(entryPrice, stopLoss, takeProfit, leverage,
                marginUsdt, notionalUsdt, maxLossUsdt, liquidationPrice);
    }
}