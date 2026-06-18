package com.fga.trader.cryptotraderfuturesbackend.ports.service;

import com.fga.tradermodel.dto.Trend;
import com.fga.tradermodel.dto.OperationParametersDto;

public interface StrategyServicePort {

    OperationParametersDto executeStrategy(String symbol, String temporality, Trend trend);
}
