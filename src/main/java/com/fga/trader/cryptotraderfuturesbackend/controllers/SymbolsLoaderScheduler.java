package com.fga.trader.cryptotraderfuturesbackend.controllers;

import com.fga.trader.cryptotraderfuturesbackend.ports.spy.BinanceSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.SymbolConfigSpyPort;
import com.fga.tradermodel.dto.SymbolConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SymbolsLoaderScheduler {

    private final SymbolConfigSpyPort symbolConfigSpyPort;
    private final BinanceSpyPort binanceSpyPort;

    @Scheduled(cron = "0 11 0 * * *", zone = "America/Mexico_City")
    public void loadActiveSymbols() {
        try {
            List<SymbolConfig> activeConfigs = binanceSpyPort.getActiveSymbols();
            activeConfigs = activeConfigs.stream().filter(s -> s.getSymbol().endsWith("USDT")).toList();
            log.info("Símbolos activos procesados: {}", activeConfigs.size());
            symbolConfigSpyPort.deleteAll();
            List<SymbolConfig> response = symbolConfigSpyPort.saveAll(activeConfigs);
//            log.info(response.toString());
        } catch (Exception e) {
            log.error("Error loading symbols: {}", e.getMessage(), e);
        }
    }
}