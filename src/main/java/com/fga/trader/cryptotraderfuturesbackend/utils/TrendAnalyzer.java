package com.fga.trader.cryptotraderfuturesbackend.utils;

import com.fga.tradermodel.dto.KlineDto;
import com.fga.tradermodel.dto.Trend;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TrendAnalyzer {

    // ── Configuración ────────────────────────────────────────────────────────
    private static final int SMA_FAST    = 20;
    private static final int SMA_SLOW    = 50;
    private static final int MACD_FAST   = 12;
    private static final int MACD_SLOW   = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int RSI_PERIOD  = 14;

    // ── Método principal ─────────────────────────────────────────────────────

    public static Trend detectTrend(List<KlineDto> klines) {
        if (klines == null || klines.size() < MACD_SLOW + MACD_SIGNAL) {
            return Trend.NEUTRAL;
        }

        int alcista = 0;
        int bajista = 0;

        // 1. Medias móviles
        Trend smaTrend = smaTrend(klines);
        if (smaTrend == Trend.ALCISTA) alcista++;
        else if (smaTrend == Trend.BAJISTA) bajista++;

        // 2. MACD
        Trend macdTrend = macdTrend(klines);
        if (macdTrend == Trend.ALCISTA) alcista++;
        else if (macdTrend == Trend.BAJISTA) bajista++;

        // 3. RSI
        Trend rsiTrend = rsiTrend(klines);
        if (rsiTrend == Trend.ALCISTA) alcista++;
        else if (rsiTrend == Trend.BAJISTA) bajista++;

        // 4. Higher Highs / Lower Lows
        Trend hhllTrend = hhllTrend(klines);
        if (hhllTrend == Trend.ALCISTA) alcista++;
        else if (hhllTrend == Trend.BAJISTA) bajista++;

        // Mayoría flexible: 3 de 4 o más
        if (alcista >= 3) return Trend.ALCISTA;
        if (bajista >= 3) return Trend.BAJISTA;
        return Trend.NEUTRAL;
    }

    // ── 1. SMA Fast vs SMA Slow ──────────────────────────────────────────────

    private static Trend smaTrend(List<KlineDto> klines) {
        if (klines.size() < SMA_SLOW) return Trend.NEUTRAL;

        double smaFast = sma(klines, SMA_FAST);
        double smaSlow = sma(klines, SMA_SLOW);

        if (smaFast > smaSlow) return Trend.ALCISTA;
        if (smaFast < smaSlow) return Trend.BAJISTA;
        return Trend.NEUTRAL;
    }

    private static double sma(List<KlineDto> klines, int period) {
        int size = klines.size();
        return klines.subList(size - period, size).stream()
                .mapToDouble(KlineDto::getClosePrice)
                .average()
                .orElse(0);
    }

    // ── 2. MACD ──────────────────────────────────────────────────────────────

    private static Trend macdTrend(List<KlineDto> klines) {
        List<Double> closes = klines.stream()
                .map(KlineDto::getClosePrice)
                .collect(Collectors.toList());

        List<Double> emaFastList  = ema(closes, MACD_FAST);
        List<Double> emaSlowList  = ema(closes, MACD_SLOW);

        // Alinear listas al tamaño de la más corta
        int minSize = Math.min(emaFastList.size(), emaSlowList.size());
        List<Double> macdLine = new ArrayList<>();
        for (int i = 0; i < minSize; i++) {
            macdLine.add(
                    emaFastList.get(emaFastList.size() - minSize + i) -
                            emaSlowList.get(emaSlowList.size() - minSize + i)
            );
        }

        List<Double> signalLine = ema(macdLine, MACD_SIGNAL);
        if (signalLine.isEmpty()) return Trend.NEUTRAL;

        double macd   = macdLine.get(macdLine.size() - 1);
        double signal = signalLine.get(signalLine.size() - 1);

        if (macd > signal) return Trend.ALCISTA;
        if (macd < signal) return Trend.BAJISTA;
        return Trend.NEUTRAL;
    }

    private static List<Double> ema(List<Double> data, int period) {
        List<Double> emas = new ArrayList<>();
        if (data.size() < period) return emas;

        double multiplier = 2.0 / (period + 1);
        double ema = data.subList(0, period).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);
        emas.add(ema);

        for (int i = period; i < data.size(); i++) {
            ema = (data.get(i) - ema) * multiplier + ema;
            emas.add(ema);
        }
        return emas;
    }

    // ── 3. RSI ───────────────────────────────────────────────────────────────

    private static Trend rsiTrend(List<KlineDto> klines) {
        if (klines.size() < RSI_PERIOD + 1) return Trend.NEUTRAL;

        double rsi = rsi(klines, RSI_PERIOD);

        if (rsi >= 55) return Trend.ALCISTA;   // momentum alcista
        if (rsi <= 45) return Trend.BAJISTA;   // momentum bajista
        return Trend.NEUTRAL;                  // zona neutral 45-55
    }

    private static double rsi(List<KlineDto> klines, int period) {
        double avgGain = 0, avgLoss = 0;
        int size = klines.size();

        for (int i = size - period; i < size; i++) {
            double change = klines.get(i).getClosePrice() - klines.get(i - 1).getClosePrice();
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }

        avgGain /= period;
        avgLoss /= period;

        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    // ── 4. Higher Highs / Lower Lows (últimas 5 velas) ──────────────────────

    private static Trend hhllTrend(List<KlineDto> klines) {
        int size = klines.size();
        if (size < 5) return Trend.NEUTRAL;

        List<KlineDto> recent = klines.subList(size - 5, size);

        boolean higherHighs = true;
        boolean higherLows  = true;
        boolean lowerHighs  = true;
        boolean lowerLows   = true;

        for (int i = 1; i < recent.size(); i++) {
            double prevHigh = recent.get(i - 1).getMaxPrice();
            double currHigh = recent.get(i).getMaxPrice();
            double prevLow  = recent.get(i - 1).getMinPrice();
            double currLow  = recent.get(i).getMinPrice();

            if (currHigh <= prevHigh) higherHighs = false;
            if (currLow  <= prevLow)  higherLows  = false;
            if (currHigh >= prevHigh) lowerHighs  = false;
            if (currLow  >= prevLow)  lowerLows   = false;
        }

        if (higherHighs && higherLows) return Trend.ALCISTA;
        if (lowerHighs  && lowerLows)  return Trend.BAJISTA;
        return Trend.NEUTRAL;
    }
}