package com.fga.trader.cryptotraderfuturesbackend.controllers;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fga.trader.cryptotraderfuturesbackend.adapters.api.SymbolConfigApi;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.BinanceSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.KlineSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.SymbolConfigSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.records.KlineLoadedEvent;
import com.fga.tradermodel.dto.KlineDto;
import com.fga.tradermodel.dto.SymbolConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api/v1/klines")
@Log4j2
@RequiredArgsConstructor
public class KlineLoaderController {

    private final SymbolConfigSpyPort symbolConfigSpyPort;
    private final KlineSpyPort klineSpyPort;
    private final BinanceSpyPort binanceSpyPort;
//    private final KlineScheduler klineScheduler;
private final UMFuturesClientImpl umFuturesClient;
    private final ApplicationEventPublisher eventPublisher;


    private final Map<String, Integer> mapKlines = Map.of("1d", 50, "4h", 60, "1h", 48, "15m", 40, "5m", 24);

//    @GetMapping
//    @RequestMapping("/loadKlines")
    public ResponseEntity<String> loadKlines() {
//        klineScheduler.schedule5m();


        return ResponseEntity.ok("success");
    }


}
