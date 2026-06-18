package com.fga.trader.cryptotraderfuturesbackend.adapters.clients;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.BinanceSpyPort;
import com.fga.tradermodel.dto.KlineDto;
import com.fga.tradermodel.dto.SymbolConfig;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@Log4j2
@AllArgsConstructor
public class BinanceClientAdapter implements BinanceSpyPort {

    private final UMFuturesClientImpl umFuturesClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<SymbolConfig> getActiveSymbols() {
        try {
            String result = umFuturesClient.market().exchangeInfo();

            JsonNode rootNode = objectMapper.readTree(result);
            JsonNode symbolsNode = rootNode.path("symbols");

            List<SymbolConfig> activeConfigs = new ArrayList<>();

            for (JsonNode symbolNode : symbolsNode) {
                String status = symbolNode.path("status").asText();
                String contractType = symbolNode.path("contractType").asText();

                if ("TRADING".equals(status) && "PERPETUAL".equals(contractType)) {
                    String symbol = symbolNode.path("symbol").asText();
                    String stepSize = null;
                    String tickSize = null;

                    JsonNode filtersNode = symbolNode.path("filters");
                    for (JsonNode filter : filtersNode) {
                        String filterType = filter.path("filterType").asText();

                        if ("LOT_SIZE".equals(filterType)) {
                            stepSize = filter.path("stepSize").asText();
                        } else if ("PRICE_FILTER".equals(filterType)) {
                            tickSize = filter.path("tickSize").asText();
                        }
                    }

                    activeConfigs.add(new SymbolConfig(symbol, stepSize, tickSize));
                }
            }
            return activeConfigs;
        } catch (Exception e) {
            log.error("error BinanceClientAdapter.getActiveSymbols", e);
            return List.of();
        }
    }

    @Override
    public List<KlineDto> klines(String symbol, LinkedHashMap<String, Object> params) {
        int maxRetries = 3;

        for (int i = 1; i <= maxRetries; i++) {
            try {
                String json = umFuturesClient.market().klines(params);
                return parseKlines(json, symbol);

            } catch (Exception e) {
                // Si es el último intento, registramos el error final
                if (i == maxRetries) {
                    log.error("❌ Fallo definitivo al obtener klines para {} tras {} intentos. Causa: {}",
                            symbol, maxRetries, e.getMessage());
                    return null; // 🟢 IMPORTANTE: Retornar null, no List.of()
                }

                // Si falló pero quedan intentos, esperamos un poco y reintentamos
                log.warn("⚠️ Timeout detectado en {}. Intento {}/{} fallido. Reintentando en {}ms...",
                        symbol, i, maxRetries, (i * 1000));
                try {
                    // Espera progresiva: 1s, luego 2s...
                    Thread.sleep(1000L * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private List<KlineDto> parseKlines(String json, String symbol) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        List<KlineDto> klines = new ArrayList<>();

        for (JsonNode kline : root) {
            KlineDto dto = new KlineDto();
            dto.setOpenTime(kline.get(0).asLong());
            dto.setOpenPrice(kline.get(1).asDouble());
            dto.setMaxPrice(kline.get(2).asDouble());
            dto.setMinPrice(kline.get(3).asDouble());
            dto.setClosePrice(kline.get(4).asDouble());
            dto.setVolume(kline.get(5).asDouble());
            dto.setCloseTime(kline.get(6).asLong());
            dto.setSymbol(symbol);
            klines.add(dto);
        }

        return klines;
    }

    @Override
    public Double getCurrentPrice(String symbol) {

//        umFuturesClient.market().
        return 0.0;
    }
}
