package com.fga.trader.cryptotraderfuturesbackend.records;

import com.fga.tradermodel.dto.Trend;

public record FVGCandidate(
        String symbol,
        String temporality,
        Trend trend,
        double c1Open, double c1Close, double c1High, double c1Low, double c1Volume,
        double c2Open, double c2Close, double c2High, double c2Low, double c2Volume,
        double c3Open, double c3Close, double c3High, double c3Low, double c3Volume,
        double gapSize,
        double proposedLimit, // el finalLimit calculado por la estrategia = stopLoss base
        double investmentUsdt,

        // --- NUEVO CONTEXTO INSTITUCIONAL SMC ---
        boolean inDiscountZone,
        boolean didSweepLiquidity,
        boolean isAlignedWithVWAP,
        double gapSizeVsATR,
        double relativeVolume,
        boolean isInKillzone
) {}