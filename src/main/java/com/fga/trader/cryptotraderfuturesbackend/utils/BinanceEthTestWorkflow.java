package com.fga.trader.cryptotraderfuturesbackend.utils;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.binance.connector.futures.client.impl.UMWebsocketClientImpl;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;

public class BinanceEthTestWorkflow {

    // 🔴 REEMPLAZA CON TUS LLAVES DE LA TESTNET
    private static final String API_KEY = "gVuGdI58TkGKxPmqNtW4ARwCRSSDDvCNOylfGM8XVbazffM2SBFUxWKYS2kC9pnb";
    private static final String SECRET_KEY = "yW7QtlaaQNZKASkIKqeAihUIJUP3YhXEf6YXyQda9eyNsegUqa18Fym83X910i6F";

    // URLs de la Testnet
    private static final String REST_URL = "https://testnet.binancefuture.com";
    private static final String WS_URL = "wss://stream.binancefuture.com";

    private static final String SYMBOL = "ETHUSDT";
    private static final int LEVERAGE = 50;
    private static final double TARGET_USD_SIZE = 500.0; // Tamaño total de la posición en USD

    public static void main(String[] args) {
        System.out.println("🚀 Iniciando Flujo de Prueba ETHUSDT...");

        UMFuturesClientImpl restClient = new UMFuturesClientImpl(API_KEY, SECRET_KEY, REST_URL);

        try {
            // ==========================================================
            // PASO 1: Configurar Apalancamiento a 100x
            // ==========================================================
            System.out.println("⚙️ 1. Configurando apalancamiento a " + LEVERAGE + "x...");
            LinkedHashMap<String, Object> leverageParams = new LinkedHashMap<>();
            leverageParams.put("symbol", SYMBOL);
            leverageParams.put("leverage", LEVERAGE);
            restClient.account().changeInitialLeverage(leverageParams);
            System.out.println("✅ Apalancamiento configurado.");

            // ==========================================================
            // PASO 2: Obtener precio actual y calcular cantidad de ETH
            // ==========================================================
            System.out.println("🔍 2. Obteniendo precio actual de " + SYMBOL + "...");
            LinkedHashMap<String, Object> priceParams = new LinkedHashMap<>();
            priceParams.put("symbol", SYMBOL);
            String tickerResponse = restClient.market().tickerSymbol(priceParams);

            JSONObject tickerJson = new JSONObject(tickerResponse);
            double currentPrice = tickerJson.getDouble("price");
            System.out.println("💵 Precio actual de ETH: $" + currentPrice);

            // Calculamos cuántos ETH necesitamos comprar para llegar a 1000 USD
            double ethQuantity = TARGET_USD_SIZE / currentPrice;

            // Binance exige que ETH tenga máximo 3 decimales en Futures
            String quantityStr = String.format(Locale.US, "%.3f", ethQuantity);
            System.out.println("🛒 Cantidad a comprar calculada: " + quantityStr + " ETH");

            // ==========================================================
            // PASO 3: Abrir Socket de Monitoreo (User Data Stream)
            // ==========================================================
            System.out.println("📡 3. Abriendo túnel de monitoreo (Socket)...");
            String listenKeyResponse = restClient.userData().createListenKey();
            String listenKey = new JSONObject(listenKeyResponse).getString("listenKey");

            UMWebsocketClientImpl wsClient = new UMWebsocketClientImpl(WS_URL);
            wsClient.listenUserStream(listenKey,
                    (event) -> System.out.println("🟢 Socket Conectado. Esperando eventos..."),
                    (message) -> {
                        try {
                            procesarEvento(message);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, // Aquí procesamos la data en tiempo real
                    (closing) -> System.out.println("🟡 Cerrando socket..."),
                    (failure) -> System.err.println("🔴 Error en socket: " + failure));

            // Damos 2 segundos para que el socket se conecte correctamente antes de lanzar la orden
            Thread.sleep(2000);

            // ==========================================================
            // PASO 4: Lanzar la Orden de Compra (LONG)
            // ==========================================================
            System.out.println("🚀 4. Lanzando Orden de Mercado LONG...");
            LinkedHashMap<String, Object> orderParams = new LinkedHashMap<>();
            orderParams.put("symbol", SYMBOL);
            orderParams.put("side", "BUY");
            orderParams.put("type", "MARKET");
            orderParams.put("quantity", quantityStr);

            String orderResponse = restClient.account().newOrder(orderParams);
            System.out.println("✅ Orden lanzada con éxito: ");
            System.out.println(new JSONObject(orderResponse).toString(2));

            // ==========================================================
            // PASO 5: Mantener la aplicación viva para seguir monitoreando
            // ==========================================================
            System.out.println("⏳ Presiona Ctrl+C para detener el monitoreo...");
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("❌ Error en el flujo de prueba: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Método para parsear e imprimir los eventos del User Data Stream.
     */
    private static void procesarEvento(String message) throws Exception {
        JSONObject event = new JSONObject(message);
        String eventType = event.optString("e", "UNKNOWN");

        switch (eventType) {
            case "ORDER_TRADE_UPDATE":
                System.out.println("\n🔔 [EVENTO: ORDEN ACTUALIZADA]");
                JSONObject orderInfo = event.getJSONObject("o");
                String status = orderInfo.getString("X"); // Status: NEW, FILLED, CANCELED
                String side = orderInfo.getString("S");   // BUY / SELL
                double filledQty = orderInfo.getDouble("z");
                double execPrice = orderInfo.getDouble("L"); // Precio exacto en que se ejecutó

                System.out.println("   -> Estado: " + status);
                System.out.println("   -> Lado: " + side);
                if (status.equals("FILLED")) {
                    System.out.println("   -> Rellenada: " + filledQty + " ETH a un precio de $" + execPrice);
                }
                break;

            case "ACCOUNT_UPDATE":
                System.out.println("\n📈 [EVENTO: POSICIÓN / MARGEN ACTUALIZADO]");
                JSONObject updateData = event.getJSONObject("a");

                // Extraer posiciones actualizadas
                if (updateData.has("P")) {
                    org.json.JSONArray positions = updateData.getJSONArray("P");

                    // 🟢 SOLUCIÓN: Usamos un bucle for tradicional en lugar de foreach
                    for (int i = 0; i < positions.length(); i++) {
                        JSONObject position = positions.getJSONObject(i);

                        if (position.getString("s").equals(SYMBOL)) {
                            double amount = position.getDouble("pa"); // Position Amount
                            double entryPrice = position.getDouble("ep"); // Entry Price
                            double unrealizedPnl = position.getDouble("up"); // PnL no realizado

                            System.out.println("   -> Símbolo: " + SYMBOL);
                            System.out.println("   -> Tamaño de Posición Abierta: " + amount + " ETH");
                            System.out.println("   -> Precio de Entrada Real: $" + entryPrice);
                            System.out.println("   -> Ganancia/Pérdida (PnL): $" + unrealizedPnl);
                        }
                    }
                }
                break;

            default:
                // Ignoramos eventos de margen general u otros pings
                break;
        }
    }
}