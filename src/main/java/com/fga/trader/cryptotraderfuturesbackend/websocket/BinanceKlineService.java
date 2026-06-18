package com.fga.trader.cryptotraderfuturesbackend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class BinanceKlineService {

    private final ObjectMapper objectMapper;
//    private final ExchangeSpyPort exchangeSpyPort;

    private final String targetInterval = "1m"; // O el intervalo que prefieras
    private static final int SYMBOLS_PER_SOCKET = 150;
    private static final String BASE_STREAM_URL = "wss://stream.binance.com:9443/stream";

    private final Map<String, ConnectionBatch> activeConnections = new ConcurrentHashMap<>();

    private static class ConnectionBatch {
        String batchId;
        WebSocketConnectionManager manager;
        BinanceKlineHandler handler;
        List<String> streams;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
//        refreshAllConnections();
    }

    public void refreshAllConnections() {
        log.info("🌐 Iniciando/Refrescando conexiones de WebSockets...");
//        List<String> symbols = exchangeSpyPort.findAllExchanges().orElse(List.of());
        List<String> symbols = List.of("ETHUSDT");
        if (symbols.isEmpty()) return;

        List<String> streams = symbols.stream()
                .map(s -> s.toLowerCase() + "@kline_" + targetInterval)
                .collect(Collectors.toList());

        List<List<String>> partitions = partitionList(streams, SYMBOLS_PER_SOCKET);

        for (int i = 0; i < partitions.size(); i++) {
            startBatchConnection(i, partitions.get(i));
        }
    }

    private void startBatchConnection(int index, List<String> streams) {
        String batchId = "batch-" + index;
        String fullUrl = BASE_STREAM_URL + "?streams=" + String.join("/", streams);

        BinanceKlineHandler handler = new BinanceKlineHandler(objectMapper, batchId);
        WebSocketConnectionManager manager = new WebSocketConnectionManager(
                new StandardWebSocketClient(), handler, fullUrl);

        manager.start();

        ConnectionBatch batch = new ConnectionBatch();
        batch.batchId = batchId;
        batch.manager = manager;
        batch.handler = handler;
        batch.streams = streams;

        activeConnections.put(batchId, batch);
    }

    /**
     * HEALTH CHECK: Se ejecuta cada 60 segundos para detectar desconexiones o sockets zombies.
     */
    @Scheduled(fixedRate = 60000)
    public void monitorConnections() {
        activeConnections.forEach((id, batch) -> {
            boolean isDisconnected = !batch.manager.isConnected();
            // Si no ha recibido mensajes en los últimos 2 minutos, lo consideramos "Zombie"
            boolean isZombie = (System.currentTimeMillis() - batch.handler.getLastMessageTime()) > 120000;

            if (isDisconnected || isZombie) {
                log.warn("🔄 Detectado problema en [{}]. Reconectando... (Desconectado: {}, Zombie: {})",
                        id, isDisconnected, isZombie);
                restartBatch(batch);
            }
        });
    }

    private void restartBatch(ConnectionBatch batch) {
        try {
            batch.manager.stop();
            // Espera pequeña para liberar recursos del sistema antes de reabrir
            Thread.sleep(2000);
            batch.manager.start();
            log.info("🚀 Reinicio de batch [{}] completado", batch.batchId);
        } catch (Exception e) {
            log.error("❌ Error reiniciando batch [{}]: {}", batch.batchId, e.getMessage());
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(new ArrayList<>(list.subList(i, Math.min(i + size, list.size()))));
        }
        return partitions;
    }
}