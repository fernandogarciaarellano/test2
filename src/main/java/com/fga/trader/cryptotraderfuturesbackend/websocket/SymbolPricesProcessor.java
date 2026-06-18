package com.fga.trader.cryptotraderfuturesbackend.websocket;

import com.fga.tradermodel.dto.KlineDetailDto;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
public class SymbolPricesProcessor {

    private final String streamId;
    private final BlockingQueue<KlineDetailDto> queue = new LinkedBlockingQueue<>();

    // Referencia atómica fundamental para compartir el último precio con el Hilo Padre
    // sin crear cuellos de botella por concurrencia.
    private final AtomicReference<KlineDetailDto> lastKline = new AtomicReference<>();
    private long lastLogTime = 0;

    public SymbolPricesProcessor(String streamId) {
        this.streamId = streamId;
        // Se levanta un hilo virtual exclusivo para procesar la cola de este símbolo
        Thread.ofVirtual().name("v-proc-" + streamId).start(this::runLoop);
    }

    public void push(KlineDetailDto kline) {
        queue.offer(kline);
    }

    /**
     * Permite al ExchangeSupervisorTask obtener el detalle de la vela más reciente
     * en cualquier milisegundo de forma segura (Thread-Safe).
     */
    public KlineDetailDto getLastKline() {
        return lastKline.get();
    }

    private void runLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // take() bloquea el hilo virtual (muy barato) hasta que llegue un nuevo dato
                KlineDetailDto kline = queue.take();
                processInternal(kline);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Hilo de procesador {} interrumpido", streamId);
        }
    }

    private void processInternal(KlineDetailDto kline) {
        // Actualizamos siempre la referencia atómica para tener el dato más fresco disponible
        // Sobrescribe el valor anterior inmediatamente para que el orquestador lo lea.
        lastKline.set(kline);

        // (Código comentado originalmente en tu implementación)
        // long currentTime = System.currentTimeMillis();
        // // Mantenemos la lógica de actualización del mapa global (opcionalmente con throttle)
        // if (currentTime - lastLogTime >= 100) {
        //     BinanceKlineService.PRICES_MAP.put(kline.symbol(), kline.close());
        //     this.lastLogTime = currentTime;
        // }
    }
}