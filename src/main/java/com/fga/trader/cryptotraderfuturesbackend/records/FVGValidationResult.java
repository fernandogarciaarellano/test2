package com.fga.trader.cryptotraderfuturesbackend.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fga.tradermodel.dto.Trend;

public record FVGValidationResult(
        @JsonProperty("isValid") boolean isValid,
        @JsonProperty("classification") String classification, // "VALID_FVG" | "NOISE"
        @JsonProperty("confidence") double confidence,         // 0.0 - 1.0
        @JsonProperty("trend") Trend trend,
        @JsonProperty("entryPrice") double entryPrice,
        @JsonProperty("stopLoss") double stopLoss,
        @JsonProperty("takeProfit") double takeProfit,
        @JsonProperty("leverage") int leverage,
        @JsonProperty("marginUsdt") double marginUsdt,       // inversión total (40 USDT)
        @JsonProperty("notionalUsdt") double notionalUsdt,   // tamaño de la posición = margen * leverage
        @JsonProperty("maxLossUsdt") double maxLossUsdt,     // pérdida estimada si toca el SL
        @JsonProperty("liquidationPrice") double liquidationPrice,
        @JsonProperty("reasoning") String reasoning
) {}