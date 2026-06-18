package com.fga.trader.cryptotraderfuturesbackend.controllers;

import com.fga.trader.cryptotraderfuturesbackend.ports.spy.BinanceOrderSpyPort;
import com.fga.tradermodel.dto.OperationParametersDto;
import com.fga.tradermodel.dto.Trend;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Log4j2
@RestController
@RequestMapping("/api/test/orders")
@RequiredArgsConstructor
public class OrderTestController {

    private final BinanceOrderSpyPort binanceOrderPort;

    /**
     * Crea una orden directamente con los parámetros recibidos.
     * SOLO PARA PRUEBAS contra el testnet.
     *
     * Ejemplo:
     * POST /api/test/orders
     * {
     *   "symbol": "SYNUSDT",
     *   "trend": "ALCISTA",
     *   "entryPrice": 0.45,
     *   "stopLoss": 0.43,
     *   "takeProfit": 0.49,
     *   "leverage": 6,
     *   "marginUsdt": 40.0,
     *   "notionalUsdt": 240.0
     * }
     */
    @PostMapping
    public ResponseEntity<String> createOrder(@RequestBody OperationParametersDto params) {
        log.info("🧪 [Test] Solicitud de creación de orden manual: symbol={} | trend={} | entry={} | SL={} | TP={} | lev={} | notional={}",
                params.getSymbol(), params.getTrend(), params.getEntryPrice(),
                params.getStopLoss(), params.getTakeProfit(), params.getLeverage(),
                params.getNotionalUsdt());

        String result = binanceOrderPort.placeOrder(params);

        if (result == null) {
            log.warn("🧪 [Test] La orden para {} no se creó (ver errores previos).", params.getSymbol());
            return ResponseEntity.unprocessableEntity()
                    .body("La orden no se creó. Revisa los logs para el detalle del error.");
        }

        log.info("🧪 [Test] Orden creada correctamente para {}: {}", params.getSymbol(), result);
        return ResponseEntity.ok(result);
    }

    /**
     * Consulta el estado de una orden existente.
     * GET /api/test/orders/status?symbol=SYNUSDT&orderId=137385711
     */
    @GetMapping("/status")
    public ResponseEntity<String> getStatus(@RequestParam String symbol, @RequestParam Long orderId) {
        String result = binanceOrderPort.getOrderStatus(symbol, orderId);
        return (result == null)
                ? ResponseEntity.unprocessableEntity().body("No se pudo consultar el estado. Revisa los logs.")
                : ResponseEntity.ok(result);
    }

    /**
     * Cancela una orden existente.
     * DELETE /api/test/orders?symbol=SYNUSDT&orderId=137385711
     */
    @DeleteMapping
    public ResponseEntity<String> cancelOrder(@RequestParam String symbol, @RequestParam Long orderId) {
        String result = binanceOrderPort.cancelOrder(symbol, orderId);
        return (result == null)
                ? ResponseEntity.unprocessableEntity().body("No se pudo cancelar la orden. Revisa los logs.")
                : ResponseEntity.ok(result);
    }

    /**
     * Consulta el balance disponible de un activo.
     * GET /api/test/orders/balance?asset=USDT
     */
    @GetMapping("/balance")
    public ResponseEntity<String> getBalance(@RequestParam(defaultValue = "USDT") String asset) {
        double balance = binanceOrderPort.getAvailableBalance(asset);
        return ResponseEntity.ok(String.format("Balance disponible de %s: %s", asset, balance));
    }
}