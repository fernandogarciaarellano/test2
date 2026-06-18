package com.fga.trader.cryptotraderfuturesbackend.controllers;

import com.fga.tradermodel.dto.KlineDto;

import java.util.List;

public class TrendAnalyzer {

    // Configuración
    private static final double TREND_THRESHOLD_PERCENT = 0.01; // 1.0%
    private static final int lookbackPeriod = 4;

    public static Trend detectTrend(List<KlineDto> klines) {

        // 1. Validar datos
        if (klines == null || klines.size() < lookbackPeriod) {
            return Trend.NEUTRAL;
        }

        // 2. Definir ventana de análisis
        // IMPORTANTE: Si estás analizando un patrón en la vela 'N',
        // deberías pasar aquí la sublista hasta 'N-1' o ajustar este índice.
        // Aquí asumimos que analizamos la tendencia de la lista entregada.
        int startIndex = klines.size() - lookbackPeriod;
        int endIndex = klines.size() - 1;

        double startOpenPrice = klines.get(startIndex).getOpenPrice().doubleValue();

        // Variables para encontrar los extremos
        double lowestLow = Double.MAX_VALUE;
        double highestHigh = Double.MIN_VALUE;

        // 3. Recorrer la ventana para encontrar Low más bajo y High más alto
        // (Corrección Robusta 2)
        for (int i = startIndex; i <= endIndex; i++) {
            KlineDto k = klines.get(i);
            double currentLow = k.getMinPrice();
            double currentHigh = k.getMaxPrice();

            if (currentLow < lowestLow) {
                lowestLow = currentLow;
            }
            if (currentHigh > highestHigh) {
                highestHigh = currentHigh;
            }
        }

        // 4. Calcular la fuerza del movimiento hacia los extremos (Drawdown y Run-up)
        double maxDropPercent = (lowestLow - startOpenPrice) / startOpenPrice;
        double maxRisePercent = (highestHigh - startOpenPrice) / startOpenPrice;

        // --- NIVEL 1: Validación por Extremos (Volatilidad) ---

        // Prioridad A: ¿Hubo un crash fuerte? (Para detectar Martillos Alcistas)
        // En tu ejemplo: (2907 - 3035) / 3035 = -0.042 (-4.2%).
        // Como -4.2% <= -1.0%, retorna BEARISH.
        if (maxDropPercent <= -TREND_THRESHOLD_PERCENT) {
            return Trend.BEARISH;
        }

        // Prioridad B: ¿Hubo un pump fuerte? (Para detectar Martillos Invertidos / Estrellas fugaces)
        if (maxRisePercent >= TREND_THRESHOLD_PERCENT) {
            return Trend.BULLISH;
        }

        // --- NIVEL 2: Validación por Mayoría de Velas (Fallback) ---
        // Si no hubo un movimiento violento de >1%, miramos la consistencia.
        // (Esto sirve para tendencias lentas y "goteo").

        int greenCandles = 0;
        int redCandles = 0;

        for (int i = startIndex; i <= endIndex; i++) {
            KlineDto kline = klines.get(i);
            double open = kline.getOpenPrice().doubleValue();
            double close = kline.getClosePrice().doubleValue();

            if (close > open) greenCandles++;
            else if (close < open) redCandles++;
        }

        double majorityThreshold = lookbackPeriod / 2.0;

        if (greenCandles > majorityThreshold) return Trend.BULLISH;
        if (redCandles > majorityThreshold) return Trend.BEARISH;

        return Trend.NEUTRAL;
    }
}