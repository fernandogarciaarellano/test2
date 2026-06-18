package com.fga.trader.cryptotraderfuturesbackend.ports.spy;

import com.fga.tradermodel.dto.KlineDto;
import com.fga.tradermodel.dto.SymbolConfig;

import java.util.LinkedHashMap;
import java.util.List;

public interface BinanceSpyPort {

    List<SymbolConfig> getActiveSymbols();

    List<KlineDto> klines(String symbol, LinkedHashMap<String, Object> params);

    Double getCurrentPrice(String symbol);
}
