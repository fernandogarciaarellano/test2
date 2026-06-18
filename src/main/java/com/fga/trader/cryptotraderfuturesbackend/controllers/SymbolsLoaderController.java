package com.fga.trader.cryptotraderfuturesbackend.controllers;

import com.binance.connector.futures.client.exceptions.BinanceClientException;
import com.binance.connector.futures.client.exceptions.BinanceConnectorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.BinanceSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.SymbolConfigSpyPort;
import com.fga.tradermodel.dto.SymbolConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class SymbolsLoaderController {

    private final SymbolConfigSpyPort symbolConfigSpyPort;
    private final BinanceSpyPort binanceSpyPort;

    //TODO convertir en scheduler
    @GetMapping("/active-symbols")
    public ResponseEntity<List<SymbolConfig>> getActiveFuturesSymbols() {

        try {

            List<SymbolConfig> activeConfigs = binanceSpyPort.getActiveSymbols();
            log.info("Símbolos activos procesados: {}", activeConfigs.size());
            symbolConfigSpyPort.deleteAll();
            List<SymbolConfig> response = symbolConfigSpyPort.saveAll(activeConfigs);
            log.info(response.toString());
            return ResponseEntity.ok(activeConfigs);

        } catch (BinanceConnectorException e) {
            log.error("Error de conexión Binance: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();

        } catch (BinanceClientException e) {
            log.error("Error de cliente Binance [Código: {}]: {}", e.getErrorCode(), e.getErrMsg(), e);
            return ResponseEntity.status(e.getHttpStatusCode()).build();

        } catch (Exception e) {
            log.error("Error loading symbols: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}