package com.fga.trader.cryptotraderfuturesbackend.ports.spy;

import com.fga.tradermodel.dto.SymbolConfig;

import java.util.List;

public interface SymbolConfigSpyPort {

    List<SymbolConfig> saveAll(List<SymbolConfig> symbols);

    List<SymbolConfig> getAll();

    void deleteAll();

    SymbolConfig findById(String symbol);

}
