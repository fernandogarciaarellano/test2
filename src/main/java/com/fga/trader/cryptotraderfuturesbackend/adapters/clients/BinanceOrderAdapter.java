package com.fga.trader.cryptotraderfuturesbackend.adapters.clients;

import com.fga.trader.cryptotraderfuturesbackend.ports.spy.BinanceOrderSpyPort;
import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.binance.connector.futures.client.exceptions.BinanceClientException;
import com.binance.connector.futures.client.exceptions.BinanceConnectorException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.SymbolConfigSpyPort;
import com.fga.tradermodel.dto.OperationParametersDto;
import com.fga.tradermodel.dto.SymbolConfig;
import com.fga.tradermodel.dto.Trend;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Log4j2
@Component
public class BinanceOrderAdapter implements BinanceOrderSpyPort {

    private final UMFuturesClientImpl umFuturesClient;
    private final SymbolConfigSpyPort symbolConfigSpyPort;
    private final ObjectMapper objectMapper;

    private final String binanceApiKey;
    private final String binanceSecretKey;
    private final String binanceBaseUrl;

    private static final String DEFAULT_STEP_SIZE = "0.001";
    private static final String DEFAULT_TICK_SIZE = "0.01";
    private static final int DEFAULT_MAX_LEVERAGE = 20;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public BinanceOrderAdapter(UMFuturesClientImpl umFuturesClient,
                               SymbolConfigSpyPort symbolConfigSpyPort,
                               ObjectMapper objectMapper,
                               @Value("${binance.api.key}") String binanceApiKey,
                               @Value("${binance.api.secret}") String binanceSecretKey,
                               @Value("${binance.base-url:https://testnet.binancefuture.com}") String binanceBaseUrl) {
        this.umFuturesClient = umFuturesClient;
        this.symbolConfigSpyPort = symbolConfigSpyPort;
        this.objectMapper = objectMapper;
        this.binanceApiKey = binanceApiKey;
        this.binanceSecretKey = binanceSecretKey;
        this.binanceBaseUrl = binanceBaseUrl;
    }

    @Override
    public String placeOrder(OperationParametersDto params) {

        String symbol = params.getSymbol();
        Trend trend = params.getTrend();

        String entrySide = (trend == Trend.ALCISTA) ? "BUY" : "SELL";
        String exitSide  = (trend == Trend.ALCISTA) ? "SELL" : "BUY";

        try {
            // 0. GARANTIZAR MARGEN ISOLATED (innegociable).
            if (!ensureIsolatedMargin(symbol)) {
                log.error("🛑 [Binance] Abortando orden para {}: no se pudo garantizar margen ISOLATED.", symbol);
                return null;
            }

            // 1. Red de seguridad: el apalancamiento del bot no puede superar el máximo del símbolo.
            int maxLeverage = getMaxLeverage(symbol);
            if (params.getLeverage() > maxLeverage) {
                log.error("🛑 [Binance] Abortando orden para {}: apalancamiento del bot ({}x) supera el " +
                                "máximo permitido por Binance ({}x). No se opera.",
                        symbol, params.getLeverage(), maxLeverage);
                return null;
            }

            // 2. Configurar el apalancamiento sugerido por el bot.
            int appliedLeverage = setLeverage(symbol, params.getLeverage());
            if (appliedLeverage != params.getLeverage()) {
                log.error("🛑 [Binance] Abortando orden para {}: apalancamiento aplicado ({}x) difiere del " +
                                "sugerido por el bot ({}x). No se abre la posición.",
                        symbol, appliedLeverage, params.getLeverage());
                return null;
            }

            // 3. Calcular y formatear cantidad (stepSize) y precio de TP (tickSize)
            double quantity = params.getNotionalUsdt() / params.getEntryPrice();
            String qtyStr = formatQuantity(symbol, quantity);
            String tpPrice = formatPrice(symbol, params.getTakeProfit());

            // 4. ORDEN DE ENTRADA a mercado
            LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
            entry.put("symbol", symbol);
            entry.put("side", entrySide);
            entry.put("type", "MARKET");
            entry.put("quantity", qtyStr);

            String entryResult = umFuturesClient.account().newOrder(entry);
            log.info("✅ [Binance] Orden de ENTRADA creada para {} ({}) con {}x ISOLATED | qty={}: {}",
                    symbol, entrySide, appliedLeverage, qtyStr, entryResult);

            // 5. TAKE PROFIT inicial vía Algo Order
            try {
                LinkedHashMap<String, Object> tp = new LinkedHashMap<>();
                tp.put("algoType", "CONDITIONAL");
                tp.put("symbol", symbol);
                tp.put("side", exitSide);
                tp.put("type", "TAKE_PROFIT");
                tp.put("quantity", qtyStr);
                tp.put("price", tpPrice);
                tp.put("triggerPrice", tpPrice);
                tp.put("timeInForce", "GTC");
                tp.put("reduceOnly", "true");

                String tpResult = sendAlgoOrder("POST", tp);
                log.info("🎯 [Binance] Take Profit (Algo) creado para {} en {}: {}", symbol, tpPrice, tpResult);

            } catch (Exception e) {
                log.error("🚨 [Binance] TP (Algo) falló para {} ({}). CERRANDO posición de emergencia.",
                        symbol, e.getMessage(), e);
                closePositionAtMarket(symbol, exitSide, qtyStr);
                return null;
            }

            return entryResult;

        } catch (BinanceConnectorException e) {
            log.error("❌ [Binance] Error de validación al crear orden para {}: {}", symbol, e.getMessage(), e);
            return null;
        } catch (BinanceClientException e) {
            log.error("❌ [Binance] Error del servidor al crear orden para {}: code={} | msg={} | http={}",
                    symbol, e.getErrorCode(), e.getErrMsg(), e.getHttpStatusCode(), e);
            return null;
        }
    }

    // ====================== CAMBIO 1: posición existente ======================

    @Override
    public boolean hasOpenPosition(String symbol) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            String result = umFuturesClient.account().positionInformation(params);

            JsonNode arr = objectMapper.readTree(result);
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    double positionAmt = node.path("positionAmt").asDouble(0.0);
                    if (Math.abs(positionAmt) > 0.0) {
                        log.debug("📌 [Binance] {} ya tiene posición abierta (positionAmt={}).", symbol, positionAmt);
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            // Falla segura: si no podemos confirmar, asumimos que SÍ hay posición para no duplicar.
            log.error("❌ [Binance] Error consultando posición de {}: {}. Se asume posición abierta por seguridad.",
                    symbol, e.getMessage(), e);
            return true;
        }
    }

    // ====================== CAMBIO 2: trailing stop ======================

    @Override
    public double getMarkPrice(String symbol) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            String result = umFuturesClient.market().markPrice(params);
            JsonNode node = objectMapper.readTree(result);
            return node.path("markPrice").asDouble(0.0);
        } catch (Exception e) {
            log.error("❌ [Binance] Error consultando markPrice de {}: {}", symbol, e.getMessage(), e);
            return 0.0;
        }
    }

    @Override
    public String placeStopLoss(String symbol, String exitSide, double stopPrice, String quantity) {
        try {
            String slStr = formatPrice(symbol, stopPrice); // formateo a tickSize aquí
            LinkedHashMap<String, Object> sl = new LinkedHashMap<>();
            sl.put("algoType", "CONDITIONAL");
            sl.put("symbol", symbol);
            sl.put("side", exitSide);
            sl.put("type", "STOP_MARKET");
            sl.put("triggerPrice", slStr);
            sl.put("quantity", quantity);
            sl.put("reduceOnly", "true");

            String result = sendAlgoOrder("POST", sl);
            log.debug("🛡️ [Binance] Stop Loss (Algo) colocado para {} en {}: {}", symbol, slStr, result);
            return result;
        } catch (Exception e) {
            log.error("❌ [Binance] Error colocando SL para {} en {}: {}", symbol, stopPrice, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void cancelAllAlgoOrders(String symbol) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            // DELETE firmado a /fapi/v1/algoOrder (cancela todas las algo del símbolo).
            String result = sendAlgoOrder("DELETE", params);
            log.debug("🧹 [Binance] Algo orders canceladas para {}: {}", symbol, result);
        } catch (Exception e) {
            log.error("❌ [Binance] Error cancelando algo orders de {}: {}", symbol, e.getMessage(), e);
        }
    }

    // ====================== Llamada firmada manual (POST/DELETE) ======================

    /**
     * Envía una petición firmada al endpoint /fapi/v1/algoOrder con el método HTTP indicado.
     * Se usa para crear (POST) y cancelar (DELETE) órdenes algo, no disponibles en el connector 3.0.5.
     */
    private String sendAlgoOrder(String httpMethod, LinkedHashMap<String, Object> params) throws Exception {
        params.put("timestamp", System.currentTimeMillis());

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (query.length() > 0) query.append("&");
            query.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(binanceSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(query.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder signature = new StringBuilder();
        for (byte b : hash) {
            signature.append(String.format("%02x", b));
        }

        String fullUrl = binanceBaseUrl + "/fapi/v1/algoOrder?" + query + "&signature=" + signature;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("X-MBX-APIKEY", binanceApiKey);

        if ("DELETE".equalsIgnoreCase(httpMethod)) {
            builder.DELETE();
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Algo order " + httpMethod + " HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    // ====================== resto de métodos ======================

    private void closePositionAtMarket(String symbol, String exitSide, String qtyStr) {
        try {
            LinkedHashMap<String, Object> close = new LinkedHashMap<>();
            close.put("symbol", symbol);
            close.put("side", exitSide);
            close.put("type", "MARKET");
            close.put("quantity", qtyStr);
            close.put("reduceOnly", "true");

            String result = umFuturesClient.account().newOrder(close);
            log.warn("🔻 [Binance] Posición de {} cerrada a mercado por fallo de TP: {}", symbol, result);
        } catch (Exception ex) {
            log.error("🆘 [Binance] CRÍTICO: no se pudo cerrar la posición de {} tras fallo de TP. " +
                    "REVISAR MANUALMENTE EN BINANCE DE INMEDIATO.", symbol, ex);
        }
    }

    private boolean ensureIsolatedMargin(String symbol) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        params.put("marginType", "ISOLATED");

        try {
            String result = umFuturesClient.account().changeMarginType(params);
            log.info("🔒 [Binance] Margen ISOLATED confirmado para {}: {}", symbol, result);
            return true;
        } catch (BinanceClientException e) {
            if (e.getErrorCode() == -4046) {
                log.debug("🔒 [Binance] {} ya estaba en ISOLATED. Continuando.", symbol);
                return true;
            }
            log.error("❌ [Binance] No se pudo establecer ISOLATED para {}: code={} | msg={}. Se ABORTA.",
                    symbol, e.getErrorCode(), e.getErrMsg(), e);
            return false;
        }
    }

    private int setLeverage(String symbol, int requestedLeverage) {
        LinkedHashMap<String, Object> lev = new LinkedHashMap<>();
        lev.put("symbol", symbol);
        lev.put("leverage", requestedLeverage);

        String result = umFuturesClient.account().changeInitialLeverage(lev);

        try {
            JsonNode node = objectMapper.readTree(result);
            int appliedLeverage = node.path("leverage").asInt();
            if (appliedLeverage != requestedLeverage) {
                log.warn("⚠️ [Binance] Apalancamiento AJUSTADO por Binance para {}: solicitado={}x | aplicado={}x.",
                        symbol, requestedLeverage, appliedLeverage);
            } else {
                log.debug("⚙️ [Binance] Apalancamiento aplicado para {}: {}x.", symbol, appliedLeverage);
            }
            return appliedLeverage;
        } catch (Exception e) {
            log.error("❌ [Binance] No se pudo parsear la respuesta de apalancamiento para {}: {}", symbol, result, e);
            return requestedLeverage;
        }
    }

//    @Override
    public int getMaxLeverage(String symbol) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            String result = umFuturesClient.account().getLeverageBracket(params);

            JsonNode root = objectMapper.readTree(result);
            JsonNode symbolNode = (root.isArray() && root.size() > 0) ? root.get(0) : root;
            JsonNode brackets = symbolNode.path("brackets");

            if (brackets.isArray() && brackets.size() > 0) {
                int maxLeverage = brackets.get(0).path("initialLeverage").asInt();
                log.debug("📐 [Binance] Apalancamiento máximo para {}: {}x", symbol, maxLeverage);
                return maxLeverage;
            }
            log.warn("⚠️ [Binance] Sin brackets para {}. Usando default {}x.", symbol, DEFAULT_MAX_LEVERAGE);
            return DEFAULT_MAX_LEVERAGE;
        } catch (Exception e) {
            log.error("❌ [Binance] Error consultando leverageBracket de {}: {}. Usando default {}x.",
                    symbol, e.getMessage(), DEFAULT_MAX_LEVERAGE, e);
            return DEFAULT_MAX_LEVERAGE;
        }
    }

    @Override
    public String getOrderStatus(String symbol, Long orderId) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("orderId", orderId);
            return umFuturesClient.account().queryOrder(params);
        } catch (BinanceClientException e) {
            log.error("❌ [Binance] Error consultando estado de orden {} ({}): {}", orderId, symbol, e.getErrMsg(), e);
            return null;
        }
    }

    @Override
    public String cancelOrder(String symbol, Long orderId) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("orderId", orderId);
            return umFuturesClient.account().cancelOrder(params);
        } catch (BinanceClientException e) {
            log.error("❌ [Binance] Error cancelando orden {} ({}): {}", orderId, symbol, e.getErrMsg(), e);
            return null;
        }
    }

    @Override
    public double getAvailableBalance(String asset) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            String result = umFuturesClient.account().futuresAccountBalance(params);

            JsonNode arr = objectMapper.readTree(result);
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    if (asset.equals(node.path("asset").asText())) {
                        double available = node.path("availableBalance").asDouble(0.0);
                        log.debug("💰 [Binance] Balance disponible de {}: {}", asset, available);
                        return available;
                    }
                }
            }
            log.warn("⚠️ [Binance] No se encontró el activo {} en el balance.", asset);
            return 0.0;
        } catch (BinanceClientException e) {
            log.error("❌ [Binance] Error consultando balance de {}: code={} | msg={}",
                    asset, e.getErrorCode(), e.getErrMsg(), e);
            return 0.0;
        } catch (Exception e) {
            log.error("❌ [Binance] Error inesperado consultando balance de {}: {}", asset, e.getMessage(), e);
            return 0.0;
        }
    }

    private SymbolConfig resolveSymbolConfig(String symbol) {
        SymbolConfig config = symbolConfigSpyPort.findById(symbol);

        if (config == null) {
            log.warn("⚠️ [Binance] Sin SymbolConfig para {}. Usando precisión por defecto (step={}, tick={}).",
                    symbol, DEFAULT_STEP_SIZE, DEFAULT_TICK_SIZE);
            return new SymbolConfig(symbol, DEFAULT_STEP_SIZE, DEFAULT_TICK_SIZE);
        }

        String step = (config.getStepSize() == null || config.getStepSize().isBlank())
                ? DEFAULT_STEP_SIZE : config.getStepSize();
        String tick = (config.getTickSize() == null || config.getTickSize().isBlank())
                ? DEFAULT_TICK_SIZE : config.getTickSize();

        if (!step.equals(config.getStepSize()) || !tick.equals(config.getTickSize())) {
            log.warn("⚠️ [Binance] SymbolConfig de {} incompleto. Aplicando defaults (step={}, tick={}).",
                    symbol, step, tick);
        }
        return new SymbolConfig(symbol, step, tick);
    }

    private String formatQuantity(String symbol, double qty) {
        SymbolConfig config = resolveSymbolConfig(symbol);
        try {
            BigDecimal step = new BigDecimal(config.getStepSize());
            return truncateToStep(BigDecimal.valueOf(qty), step);
        } catch (NumberFormatException e) {
            log.error("❌ [Binance] stepSize inválido ('{}') para {}. Usando default {}.",
                    config.getStepSize(), symbol, DEFAULT_STEP_SIZE);
            return truncateToStep(BigDecimal.valueOf(qty), new BigDecimal(DEFAULT_STEP_SIZE));
        }
    }

    private String formatPrice(String symbol, double price) {
        SymbolConfig config = resolveSymbolConfig(symbol);
        try {
            BigDecimal tick = new BigDecimal(config.getTickSize());
            return truncateToStep(BigDecimal.valueOf(price), tick);
        } catch (NumberFormatException e) {
            log.error("❌ [Binance] tickSize inválido ('{}') para {}. Usando default {}.",
                    config.getTickSize(), symbol, DEFAULT_TICK_SIZE);
            return truncateToStep(BigDecimal.valueOf(price), new BigDecimal(DEFAULT_TICK_SIZE));
        }
    }

    private String truncateToStep(BigDecimal value, BigDecimal step) {
        if (step.signum() == 0) {
            return value.toPlainString();
        }
        BigDecimal multiples = value.divide(step, 0, RoundingMode.DOWN);
        BigDecimal truncated = multiples.multiply(step);
        return truncated.setScale(step.stripTrailingZeros().scale(), RoundingMode.DOWN).toPlainString();
    }
}