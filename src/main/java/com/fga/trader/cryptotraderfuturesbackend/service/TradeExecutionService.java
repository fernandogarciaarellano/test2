package com.fga.trader.cryptotraderfuturesbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fga.trader.cryptotraderfuturesbackend.ports.service.StrategyServicePort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.BinanceOrderSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.OrderSpyPort;
import com.fga.tradermodel.dto.OperationParametersDto;
import com.fga.tradermodel.dto.OrderDto;
import com.fga.tradermodel.dto.Trend;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class TradeExecutionService {

    private final OrderSpyPort orderSpyPort;
    private final StrategyServicePort strategyServicePort;
    private final BinanceOrderSpyPort binanceOrderPort;
    private final OrderMonitorService orderMonitorService;
    private final ObjectMapper objectMapper;

    public void evaluateAndExecute(String symbol, String interval, Trend trend) {
        try {
            log.debug("🧵 [Hilo Virtual] Evaluando estrategia para {} en {}", symbol, interval);

            OperationParametersDto params = strategyServicePort.executeStrategy(symbol, interval, trend);

            if (params == null) {
                log.debug("⏸️ No se cumplieron las condiciones de entrada para {}", symbol);
                return;
            }

            // 🚫 CAMBIO 1: si ya hay posición en el símbolo, no se abre otra.
            if (binanceOrderPort.hasOpenPosition(symbol)) {
                log.info("⏭️ [TradeExecution] {} ya tiene una posición abierta. Se omite la nueva operación.", symbol);
                return;
            }

            // 💰 Validación de balance: el margen requerido no puede superar el disponible.
            double availableBalance = binanceOrderPort.getAvailableBalance("USDT");
            double requiredMargin = params.getMarginUsdt();

            if (availableBalance < requiredMargin) {
                log.warn("🚫 Balance insuficiente para {}: disponible={} USDT | requerido={} USDT. No se crea la orden.",
                        symbol, availableBalance, requiredMargin);
                return;
            }

            log.info("✅ Estrategia cumplida para {}. Balance suficiente (disp={} USDT, req={} USDT). Creando orden...",
                    symbol, availableBalance, requiredMargin);

            String orderJson = binanceOrderPort.placeOrder(params);

            if (orderJson == null) {
                log.warn("⚠️ La orden para {} no se creó (ver errores previos). No se inicia monitoreo.", symbol);
                return;
            }

            JsonNode node = objectMapper.readTree(orderJson);
            Long orderId = node.path("orderId").asLong();

            log.info("📨 Orden {} creada para {}. Iniciando monitoreo de estado.", orderId, symbol);

            // Persistir el registro de la orden
            OrderDto orderRecord = OrderDto.builder()
                    .orderId(orderId)
                    .symbol(symbol)
                    .temporality(interval)
                    .trend(trend)
                    .side(node.path("side").asText(null))
                    .entryPrice(params.getEntryPrice())
                    .stopLoss(params.getStopLoss())
                    .takeProfit(params.getTakeProfit())
                    .leverage(params.getLeverage())
                    .marginUsdt(params.getMarginUsdt())
                    .notionalUsdt(params.getNotionalUsdt())
                    .maxLossUsdt(params.getMaxLossUsdt())
                    .liquidationPrice(params.getLiquidationPrice())
                    .executedQty(node.path("executedQty").asDouble(0.0))
                    .avgFillPrice(node.path("avgPrice").asDouble(0.0))
                    .status(node.path("status").asText(null))
                    .confidence(params.getConfidence())
                    .classification(params.getClassification())
                    .reasoning(params.getReasoning())
                    .build();

//            orderSpyPort.save(orderRecord);
            log.info("💾 [TradeExecution] Orden persistida en BD: {}", orderRecord);

            // Cantidad ejecutada (para el trailing). Usa la real si viene; si no, recalcúlala.
            String qtyStr = node.path("origQty").asText(
                    String.valueOf(params.getNotionalUsdt() / params.getEntryPrice()));

            // 🔄 CAMBIO 2: arrancar el trailing stop
            orderMonitorService.monitorOrder(
                    symbol, trend, params.getEntryPrice(), params.getStopLoss(), qtyStr);

        } catch (Exception e) {
            log.error("❌ Error ejecutando la estrategia para {} {}: {}", symbol, interval, e.getMessage(), e);
        }
    }
}