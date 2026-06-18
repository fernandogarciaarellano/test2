package com.fga.trader.cryptotraderfuturesbackend.service;

import com.fga.trader.cryptotraderfuturesbackend.ports.spy.KlineSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.records.KlineLoadedEvent;
import com.fga.trader.cryptotraderfuturesbackend.utils.TrendAnalyzer;
import com.fga.tradermodel.dto.KlineDto;
import com.fga.tradermodel.dto.Trend;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Log4j2
@RequiredArgsConstructor
public class TrendService {

    private final KlineSpyPort klineSpyPort;
    private final TradeExecutionService tradeExecutionService;

    @Async // Permite que el Scheduler continúe descargando sin esperar a que termine este bloque
    @EventListener
    public void onKlineLoaded(KlineLoadedEvent event) {
        String symbol = event.symbol();
        String interval = event.interval();

        List<KlineDto> klines = fetchAllKlines(symbol, interval);

        if (klines != null && !klines.isEmpty()) {
            Trend trend = TrendAnalyzer.detectTrend(klines);

            if (trend != Trend.NEUTRAL) {

                // 1. Validamos la calidad mediante alineación Multi-Timeframe (Escalonada)
                if (isTrendConfirmed(symbol, interval, trend)) {

//                    log.info("🚀 Calidad de Gap/Tendencia validada para {} {}. Lanzando Hilo Virtual...", symbol, interval);

                    // 2. Lanzamiento del Hilo Virtual (Java 21) solo si la tendencia fue confirmada
                    Thread.startVirtualThread(() -> {
                        tradeExecutionService.evaluateAndExecute(symbol, interval, trend);
                    });
                }
            }
        }
    }

    /**
     * Motor de reglas escalonadas Multi-Timeframe.
     * Define qué temporalidad mayor debe validar a la temporalidad actual.
     */
    private boolean isTrendConfirmed(String symbol, String baseInterval, Trend baseTrend) {
        return switch (baseInterval) {
            case "1d"  -> {
                log.info("👑 Tendencia {} en 1d aceptada automáticamente (Es la temporalidad maestra)", baseTrend);
                yield true; // 1d no necesita confirmación de nadie
            }
            case "4h"  -> confirmWithHigherTimeframe(symbol, baseInterval, "1d", baseTrend);
            case "1h"  -> confirmWithHigherTimeframe(symbol, baseInterval, "4h", baseTrend);
            case "15m" -> confirmWithHigherTimeframe(symbol, baseInterval, "1h", baseTrend);
            default    -> {
                log.warn("⚠️ Temporalidad {} no sujeta a reglas de confirmación. Se rechaza por seguridad.", baseInterval);
                yield false;
            }
        };
    }

    /**
     * Compara la tendencia actual con la tendencia de la temporalidad superior.
     */
    private boolean confirmWithHigherTimeframe(String symbol, String baseInterval, String higherInterval, Trend baseTrend) {
        List<KlineDto> higherKlines = fetchAllKlines(symbol, higherInterval);

        if (higherKlines == null || higherKlines.isEmpty()) {
            log.debug("⏸️ No hay datos suficientes en {} para confirmar la tendencia de {}", higherInterval, symbol);
            return false;
        }

        Trend higherTrend = TrendAnalyzer.detectTrend(higherKlines);
        boolean isConfirmed = (baseTrend == higherTrend);

//        if (isConfirmed) {
//            log.info("✅ ALINEACIÓN MTF: Tendencia {} de {} confirmada por temporalidad mayor ({})",
//                    baseTrend, baseInterval, higherInterval);
//        } else {
//            log.debug("❌ FILTRO MTF: Tendencia {} de {} rechazada. La temporalidad mayor ({}) está en {}",
//                    baseTrend, baseInterval, higherInterval, higherTrend);
//        }

        return isConfirmed;
    }

    private List<KlineDto> fetchAllKlines(String symbol, String interval) {
        return switch (interval) {
            case "1d"  -> klineSpyPort.getAllBySymbol1d(symbol);
            case "4h"  -> klineSpyPort.getAllBySymbol4h(symbol);
            case "1h"  -> klineSpyPort.getAllBySymbol1h(symbol);
            case "15m" -> klineSpyPort.getAllBySymbol15m(symbol);
            case "5m"  -> klineSpyPort.getAllBySymbol5m(symbol);
            default    -> {
                log.warn("Intervalo no reconocido para análisis: {}", interval);
                yield List.of();
            }
        };
    }
}