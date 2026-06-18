package com.fga.trader.cryptotraderfuturesbackend.records;

import com.fga.tradermodel.dto.Trend;

public record FVGPosition(String symbol, Double positionLimit, String type, Trend trend) {
}
