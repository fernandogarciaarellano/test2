package com.fga.trader.cryptotraderfuturesbackend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fga.tradermodel.dto.CombinedKlineWrapperDto;
import com.fga.tradermodel.dto.KlineDetailDto;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class BinanceKlineHandler extends TextWebSocketHandler {

    private static final Map<String, SymbolPricesProcessor> GLOBAL_PROCESSORS = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final String connectionId;

    @Getter
    private long lastMessageTime = System.currentTimeMillis(); // Rastreador de actividad

    public BinanceKlineHandler(ObjectMapper objectMapper, String connectionId) {
        this.objectMapper = objectMapper;
        this.connectionId = connectionId;
    }

    public static SymbolPricesProcessor getProcessor(String symbol) {
        return GLOBAL_PROCESSORS.computeIfAbsent(symbol.toUpperCase(),
                s -> new SymbolPricesProcessor(s));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[{}] ✅ Conexión establecida con Binance", connectionId);
        this.lastMessageTime = System.currentTimeMillis();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        this.lastMessageTime = System.currentTimeMillis(); // Actualizar en cada mensaje
        try {
            String payload = message.getPayload();
            if (payload.contains("\"result\":null") || payload.contains("ping")) return;

            CombinedKlineWrapperDto wrapper = objectMapper.readValue(payload, CombinedKlineWrapperDto.class);
            KlineDetailDto kline = wrapper.data().kline();
            String symbol = kline.symbol().toUpperCase();

            getProcessor(symbol).push(kline);
        } catch (Exception e) {
            log.error("[{}] Error procesando mensaje: {}", connectionId, e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[{}] ❌ Error de transporte: {}", connectionId, exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        log.warn("[{}] ⚠️ Conexión cerrada: {}", connectionId, status.getReason());
    }
}