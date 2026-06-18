package com.fga.trader.cryptotraderfuturesbackend.controllers;

import com.fga.trader.cryptotraderfuturesbackend.adapters.api.SymbolConfigApi;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.BinanceSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.KlineSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.SymbolConfigSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.records.KlineLoadedEvent;
import com.fga.tradermodel.dto.KlineDto;
import com.fga.tradermodel.dto.SymbolConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Log4j2
@RequiredArgsConstructor
public class KlineScheduler {

    private final SymbolsLoaderScheduler symbolsLoaderScheduler;
    private final SymbolConfigSpyPort symbolConfigSpyPort;
    private final KlineSpyPort klineSpyPort;
    private final BinanceSpyPort binanceSpyPort;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, Integer> mapKlines = Map.of("1d", 50, "4h", 60, "1h", 60, "15m", 60, "5m", 60);

    // 🟢 Candado Justo: Obliga a los schedulers a formarse en fila india según el orden en que fueron llamados.
    private final ReentrantLock lock = new ReentrantLock(true);

    // 🛡️ Bandera atómica de seguridad: Bloquea los Schedulers hasta que el arranque termine
    private final AtomicBoolean isStartupComplete = new AtomicBoolean(false);

    // =========================================================================================
    // 🚀 NUEVO: PROCESO DE ARRANQUE ESCALONADO (STARTUP)
    // =========================================================================================
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationStartup() {
        log.info("🚀 Aplicación iniciada. Iniciando secuencia estricta de sincronización...");

        Thread.startVirtualThread(() -> {
            long startupBegin = System.currentTimeMillis();
            try {
                // FASE 1: Carga de Temporalidades Mayores (HTF - Higher Timeframes)
                log.info("⏳ Fase 1: Sincronizando temporalidades maestras (1d, 4h, 1h)...");
                symbolsLoaderScheduler.loadActiveSymbols();
                ejecutarPorSymbol("1h");
                ejecutarPorSymbol("4h");
                ejecutarPorSymbol("1d");

                // FASE 2: Carga de Temporalidades Menores (LTF)
                log.info("⏳ Fase 2: Sincronizando temporalidades operativas (15m, 5m)...");
                ejecutarPorSymbol("15m");
//                ejecutarPorSymbol("5m");

                // 🟢 Se libera el bloqueo para permitir que los Cron Jobs comiencen a operar
                isStartupComplete.set(true);
                log.info("✅ Sincronización de arranque finalizada con éxito en {} ms. Bot listo para operar.",
                        System.currentTimeMillis() - startupBegin);
            } catch (Exception e) {
                log.error("❌ Error durante la sincronización de arranque: {}", e.getMessage(), e);
            }
        });
    }

    // =========================================================================================
    // ⏰ CRON JOBS HABITUALES
    // =========================================================================================

    @Scheduled(cron = "30 0 18 * * *", zone = "America/Mexico_City")
    public void schedule1d() {
        if (!isStartupComplete.get()) {
            log.debug("⛔ Scheduler 1d ignorado: el arranque aún no ha finalizado.");
            return;
        }
        log.info("⏰ Scheduler 1d encolado");
        ejecutarPorSymbol("1d");
    }

    @Scheduled(cron = "25 0 18,22,2,6,10,14 * * *", zone = "America/Mexico_City")
    public void schedule4h() {
        if (!isStartupComplete.get()) {
            log.debug("⛔ Scheduler 4h ignorado: el arranque aún no ha finalizado.");
            return;
        }
        log.info("⏰ Scheduler 4h encolado");
        ejecutarPorSymbol("4h");
    }

    @Scheduled(cron = "20 0 * * * *", zone = "America/Mexico_City")
    public void schedule1h() {
        if (!isStartupComplete.get()) {
            log.debug("⛔ Scheduler 1h ignorado: el arranque aún no ha finalizado.");
            return;
        }
        log.info("⏰ Scheduler 1h encolado");
        ejecutarPorSymbol("1h");
    }

    @Scheduled(cron = "10 0/15 * * * *", zone = "America/Mexico_City")
    public void schedule15m() {
        if (!isStartupComplete.get()) {
            log.debug("⛔ Scheduler 15m ignorado: el arranque aún no ha finalizado.");
            return;
        }
        log.info("⏰ Scheduler 15m encolado");
        ejecutarPorSymbol("15m");
    }

    //    @Scheduled(cron = "5 0/5 * * * *", zone = "America/Mexico_City")
    public void schedule5m() {
        if (!isStartupComplete.get()) {
            log.debug("⛔ Scheduler 5m ignorado: el arranque aún no ha finalizado.");
            return;
        }
        log.info("⏰ Scheduler 5m encolado");
        ejecutarPorSymbol("5m");
    }

    // =========================================================================================
    // 🧠 MOTOR PRINCIPAL DE DESCARGA Y SINCRONIZACIÓN
    // =========================================================================================

    private void ejecutarPorSymbol(String interval) {
        log.debug("🔓 [{}] Solicitando candado de ejecución...", interval);
        // 🟢 Si otro scheduler está corriendo, el hilo se pausa aquí hasta que sea su turno
        lock.lock();
        log.debug("🔒 [{}] Candado adquirido. Iniciando sincronización.", interval);
        try {
            long startExecution = System.currentTimeMillis();

            var responseBody = symbolConfigSpyPort.getAll();
            if (responseBody == null) {
                log.warn("⚠️ [{}] No se pudieron obtener los símbolos de la base de datos (respuesta NULL).", interval);
                return;
            }
            if (responseBody.isEmpty()) {
                log.warn("⚠️ [{}] La lista de símbolos está vacía. No hay nada que sincronizar.", interval);
                return;
            }

            List<String> symbols = responseBody.stream().map(SymbolConfig::getSymbol).toList();
            log.info("📋 [{}] Sincronizando {} símbolos.", interval, symbols.size());

            int procesados = 0;
            int actualizados = 0;
            int sinCambios = 0;
            int conError = 0;
            int eventosEmitidos = 0;

            for (String symbol : symbols) {
                try {
                    procesados++;

                    // 1. Obtener la más antigua y la más reciente de la base de datos
                    KlineDto oldest = getOldest(interval, symbol);
                    KlineDto newest = getNewest(interval, symbol);

                    boolean isInitialLoad = (oldest == null || newest == null);
                    long intervalMs = getIntervalMillis(interval);
                    long endTime    = getLastCloseTime(interval);

                    int limit;

                    if (isInitialLoad) {
                        limit = mapKlines.get(interval);
                        log.debug("🆕 [{}] {} sin datos previos. Carga inicial de {} velas.", interval, symbol, limit);
                    } else {
                        // 2. MATEMÁTICA DE RECUPERACIÓN (Self-Healing)
                        long expectedLatestOpenTime = endTime - intervalMs + 1;
                        long missingMs = expectedLatestOpenTime - newest.getOpenTime();

                        limit = (int) (missingMs / intervalMs);

                        // Si por alguna razón el límite calculado excede la capacidad máxima, forzar carga inicial
                        if (limit > mapKlines.get(interval)) {
                            log.warn("🩹 [{}] {} con desfase grande ({} velas faltantes). Forzando recarga completa de {} velas.",
                                    interval, symbol, limit, mapKlines.get(interval));
                            limit = mapKlines.get(interval);
                            isInitialLoad = true;
                        } else if (limit > 0) {
                            log.debug("🔄 [{}] {} actualizando {} velas faltantes (self-healing).", interval, symbol, limit);
                        }
                    }

                    // 3. OPTIMIZACIÓN: Si estamos actualizados, evitamos el consumo de API
                    if (limit <= 0) {
                        sinCambios++;
                        log.debug("✅ [{}] {} ya está actualizado. Se omite llamada a Binance.", interval, symbol);
                        continue;
                    }

                    long startTime = getStartTime(interval, endTime, limit);

                    LinkedHashMap<String, Object> params = new LinkedHashMap<>();
                    params.put("symbol", symbol);
                    params.put("interval", interval);
                    params.put("startTime", startTime);
                    params.put("endTime", endTime);
                    params.put("limit", limit);

                    List<KlineDto> result = binanceSpyPort.klines(symbol, params);

                    if (result == null || result.isEmpty()) {
                        log.warn("📭 [{}] Binance no devolvió velas para {} (limit solicitado={}). Posible símbolo inactivo o sin datos en el rango.",
                                interval, symbol, limit);
                        continue;
                    }

                    log.debug("📥 [{}] {} velas recibidas de Binance para {}.", interval, result.size(), symbol);

                    // 4. MANTENIMIENTO DE LA VENTANA (Sliding Window)
                    if (isInitialLoad) {
                        saveAll(interval, result);
                        log.debug("💾 [{}] Carga inicial guardada para {} ({} velas).", interval, symbol, result.size());
                    } else {
                        // Se eliminan dinámicamente tantas velas antiguas como velas nuevas fueron recuperadas
                        deleteOldestN(interval, symbol, result.size());
                        saveAll(interval, result);
                        log.debug("💾 [{}] Sliding window aplicado para {}: -{} antiguas / +{} nuevas.",
                                interval, symbol, result.size(), result.size());
                    }
                    actualizados++;

                    // 🟢 EXCLUSIÓN DE LA TEMPORALIDAD DE 5M Y 1D
                    // Solo se emite el evento de validación de estrategia si la temporalidad no es de 5m ni 1d
                    if (!"5m".equals(interval) && !"1d".equals(interval)) {
                        eventPublisher.publishEvent(new KlineLoadedEvent(symbol, interval));
                        eventosEmitidos++;
                        log.debug("📡 [{}] Evento KlineLoadedEvent emitido para {} → dispara análisis de estrategia.", interval, symbol);
                    } else {
                        log.debug("📉 [{}] Velas guardadas para {}. Se omite el análisis de estrategia (temporalidad excluida).", interval, symbol);
                    }

                    // 🟢 Limitador de API integrado: 35ms = ~28 req/sec = ~1680 req/min
                    Thread.sleep(35);

                } catch (Exception e) {
                    conError++;
                    log.error("❌ [{}] Error procesando {} : {}", interval, symbol, e.getMessage(), e);
                }
            }

            log.info("🏁 [{}] Fin de ejecución. Procesados: {} | Actualizados: {} | Sin cambios: {} | Con error: {} | Eventos emitidos: {} | Tiempo: {} ms",
                    interval, procesados, actualizados, sinCambios, conError, eventosEmitidos,
                    (System.currentTimeMillis() - startExecution));

        } catch (Exception e) {
            log.error("❌ [{}] Error crítico en orquestador de símbolos: {}", interval, e.getMessage(), e);
        } finally {
            // 🟢 CRÍTICO: Siempre liberar el candado pase lo que pase, para no bloquear la aplicación
            lock.unlock();
            log.debug("🔓 [{}] Candado liberado.", interval);
        }
    }

    private long getLastCloseTime(String interval) {
        long now = System.currentTimeMillis();
        long intervalMs = getIntervalMillis(interval);
        return (now / intervalMs) * intervalMs - 1;
    }

    private long getStartTime(String interval, long endTime, int limit) {
        long intervalMs = getIntervalMillis(interval);
        return endTime - (intervalMs * limit) + 1;
    }

    private long getIntervalMillis(String interval) {
        return switch (interval) {
            case "1d"  -> 24 * 60 * 60 * 1000L;
            case "4h"  -> 4  * 60 * 60 * 1000L;
            case "1h"  -> 60 * 60 * 1000L;
            case "15m" -> 15 * 60 * 1000L;
            case "5m"  -> 5  * 60 * 1000L;
            default    -> throw new IllegalArgumentException("Intervalo no reconocido: " + interval);
        };
    }

    private KlineDto getOldest(String interval, String symbol) {
        return switch (interval) {
            case "1d"  -> klineSpyPort.getOldest1d(symbol);
            case "4h"  -> klineSpyPort.getOldest4h(symbol);
            case "1h"  -> klineSpyPort.getOldest1h(symbol);
            case "15m" -> klineSpyPort.getOldest15m(symbol);
            case "5m"  -> klineSpyPort.getOldest5m(symbol);
            default    -> null;
        };
    }

    private KlineDto getNewest(String interval, String symbol) {
        return switch (interval) {
            case "1d"  -> klineSpyPort.getNewest1d(symbol);
            case "4h"  -> klineSpyPort.getNewest4h(symbol);
            case "1h"  -> klineSpyPort.getNewest1h(symbol);
            case "15m" -> klineSpyPort.getNewest15m(symbol);
            case "5m"  -> klineSpyPort.getNewest5m(symbol);
            default    -> null;
        };
    }

    private void deleteOldestN(String interval, String symbol, int limit) {
        switch (interval) {
            case "1d"  -> klineSpyPort.deleteOldestN1d(symbol, limit);
            case "4h"  -> klineSpyPort.deleteOldestN4h(symbol, limit);
            case "1h"  -> klineSpyPort.deleteOldestN1h(symbol, limit);
            case "15m" -> klineSpyPort.deleteOldestN15m(symbol, limit);
            case "5m"  -> klineSpyPort.deleteOldestN5m(symbol, limit);
            default    -> log.warn("Intervalo no reconocido para borrar: {}", interval);
        }
    }

    private void saveAll(String interval, List<KlineDto> klines) {
        switch (interval) {
            case "1d"  -> klineSpyPort.saveAll1d(klines);
            case "4h"  -> klineSpyPort.saveAll4h(klines);
            case "1h"  -> klineSpyPort.saveAll1h(klines);
            case "15m" -> klineSpyPort.saveAll15m(klines);
            case "5m"  -> klineSpyPort.saveAll5m(klines);
            default    -> log.warn("Intervalo no reconocido para guardar: {}", interval);
        }
    }
}