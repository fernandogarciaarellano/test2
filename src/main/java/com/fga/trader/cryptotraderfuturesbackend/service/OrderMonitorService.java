package com.fga.trader.cryptotraderfuturesbackend.service;

import com.fga.trader.cryptotraderfuturesbackend.ports.spy.BinanceOrderSpyPort;
import com.fga.tradermodel.dto.Trend;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class OrderMonitorService {

    private final BinanceOrderSpyPort binanceOrderPort;

    private static final long POLL_INTERVAL_MS = 5000;
    private static final long MAX_MONITOR_MS = 24 * 60 * 60 * 1000L;

    // % fijo detrás del precio para el trailing SL (0.012 = 1.2%)
    private static final double TRAILING_PCT = 0.012;

    /**
     * Monitorea la posición. Al alcanzar 1R de ganancia, cancela el TP fijo y activa un trailing
     * stop a TRAILING_PCT detrás del precio, que solo se mueve a favor (nunca en contra).
     * Asegura ganancia y deja correr el beneficio.
     */
    public void monitorOrder(String symbol, Trend trend, double entryPrice,
                             double originalStopLoss, String quantity) {
        Thread.startVirtualThread(() -> {
            log.info("👁️ [Monitor] Trailing iniciado para {} | entry={} | SL inicial={} | trend={}",
                    symbol, entryPrice, originalStopLoss, trend);

            boolean isLong = (trend == Trend.ALCISTA);
            String exitSide = isLong ? "SELL" : "BUY";

            double oneR = Math.abs(entryPrice - originalStopLoss);
            double activationPrice = isLong ? entryPrice + oneR : entryPrice - oneR;

            boolean trailingActive = false;
            double currentStopLoss = originalStopLoss;
            long start = System.currentTimeMillis();

            try {
                while (System.currentTimeMillis() - start < MAX_MONITOR_MS) {

                    if (!binanceOrderPort.hasOpenPosition(symbol)) {
                        log.info("✅ [Monitor] {} ya no tiene posición abierta. Fin del monitoreo.", symbol);
                        return;
                    }

                    double price = binanceOrderPort.getMarkPrice(symbol);
                    if (price <= 0) {
                        Thread.sleep(POLL_INTERVAL_MS);
                        continue;
                    }

                    if (!trailingActive) {
                        boolean reached = isLong ? price >= activationPrice : price <= activationPrice;
                        if (reached) {
                            log.info("🎯 [Monitor] {} alcanzó 1R (precio={}, activación={}). " +
                                    "Cancelando TP fijo y activando trailing.", symbol, price, activationPrice);
                            binanceOrderPort.cancelAllAlgoOrders(symbol); // cancela TP fijo
                            trailingActive = true;
                            currentStopLoss = computeTrailingStop(isLong, price);
                            placeTrailing(symbol, exitSide, currentStopLoss, quantity);
                        }
                    } else {
                        double candidate = computeTrailingStop(isLong, price);
                        boolean improved = isLong ? candidate > currentStopLoss : candidate < currentStopLoss;
                        if (improved) {
                            currentStopLoss = candidate;
                            binanceOrderPort.cancelAllAlgoOrders(symbol); // quitar SL trailing anterior
                            placeTrailing(symbol, exitSide, currentStopLoss, quantity);
                            log.info("🔼 [Monitor] {} trailing SL ajustado a {} (precio={}).",
                                    symbol, currentStopLoss, price);
                        }
                    }

                    Thread.sleep(POLL_INTERVAL_MS);
                }
                log.warn("⏰ [Monitor] Tiempo máximo de monitoreo alcanzado para {}.", symbol);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("❌ [Monitor] Monitoreo interrumpido para {}.", symbol);
            } catch (Exception e) {
                log.error("❌ [Monitor] Error en trailing de {}: {}", symbol, e.getMessage(), e);
            }
        });
    }

    /**
     * SL a TRAILING_PCT detrás del precio. Long: price*(1-pct). Short: price*(1+pct).
     */
    private double computeTrailingStop(boolean isLong, double price) {
        return isLong ? price * (1 - TRAILING_PCT) : price * (1 + TRAILING_PCT);
    }

    private void placeTrailing(String symbol, String exitSide, double stopLoss, String quantity) {
        String result = binanceOrderPort.placeStopLoss(symbol, exitSide, stopLoss, quantity);
        if (result == null) {
            log.error("🆘 [Monitor] No se pudo colocar el trailing SL para {} en {}. POSICIÓN EN RIESGO.",
                    symbol, stopLoss);
        }
    }
}