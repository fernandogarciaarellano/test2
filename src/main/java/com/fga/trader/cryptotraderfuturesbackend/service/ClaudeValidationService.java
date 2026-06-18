package com.fga.trader.cryptotraderfuturesbackend.adapters.spy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fga.trader.cryptotraderfuturesbackend.config.ClaudeProperties;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.ClaudeValidationPort;
import com.fga.trader.cryptotraderfuturesbackend.records.ClaudeVerdict;
import com.fga.trader.cryptotraderfuturesbackend.records.FVGCandidate;
import com.fga.tradermodel.dto.Trend;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class ClaudeValidationService implements ClaudeValidationPort {

    private final ClaudeProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeValidationService(ClaudeProperties props) {
        this.props = props;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.getConnectTimeoutSeconds()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(props.getReadTimeoutSeconds()));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(props.getBaseUrl())
                .defaultHeader("x-api-key", props.getApiKey())
                .defaultHeader("anthropic-version", props.getVersion())
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public ClaudeVerdict validateFvg(FVGCandidate c) {
        String direction = c.trend() == Trend.ALCISTA ? "LONG (Alcista)" : "SHORT (Bajista)";

        String prompt = """
                Eres un Trader Institucional Algorítmico experto en SMC (Smart Money Concepts).
                Evalúa si el siguiente patrón de 3 velas (C1, C2, C3) forma un Fair Value Gap (FVG) OPERABLE y de ALTA PROBABILIDAD en el par %s (%s).

                ## DATOS DEL MERCADO Y VOLATILIDAD
                Dirección buscada: %s
                Tamaño del Gap vs ATR: %.2f (Debe ser > 0.5 para ser relevante, menor a eso es ruido)
                Volumen Relativo (RVOL): %.2f (C2 vs promedio de 20 periodos. > 1.5 indica inyección institucional)

                ## CONTEXTO SMC AVANZADO
                ¿Está en Zona Premium/Discount correcta?: %b (True = Excelente relación riesgo/beneficio)
                ¿Barrió Liquidez previa (Stop Hunt)?: %b (True = Altísima probabilidad direccional)
                ¿Alineado con el VWAP rodante?: %b (True = Flujo de órdenes a favor)
                ¿Ocurrió en Killzone (Alta volatilidad)?: %b

                ## VELAS (OHLCV)
                C1 -> Open: %.8f | Close: %.8f | High: %.8f | Low: %.8f | Vol: %.2f
                C2 -> Open: %.8f | Close: %.8f | High: %.8f | Low: %.8f | Vol: %.2f
                C3 -> Open: %.8f | Close: %.8f | High: %.8f | Low: %.8f | Vol: %.2f

                ## CRITERIOS ESTRICTOS DE CLASIFICACIÓN
                - Si RVOL < 1.0, inclínate fuertemente a clasificarlo como NOISE (ruido algorítmico sin respaldo de volumen).
                - Si 'Barrió Liquidez previa' es falso y no está en 'Killzone', reduce drásticamente tu confianza.
                - FVG ALCISTA válido: C3 Low > C1 High. FVG BAJISTA válido: C3 High < C1 Low.
                - El contexto SMC Avanzado es CRÍTICO. Usa los valores booleanos y el RVOL para definir tu razonamiento.

                ## FORMATO DE RESPUESTA
                Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional ni markdown:
                {
                  "isValid": boolean,
                  "classification": "VALID_FVG" | "NOISE",
                  "confidence": number (0.0 a 1.0),
                  "reasoning": "Explicación técnica detallada fundamentada en el RVOL, ATR y Contexto SMC provisto."
                }
                """.formatted(
                c.symbol(), c.temporality(), direction, c.gapSizeVsATR(), c.relativeVolume(),
                c.inDiscountZone(), c.didSweepLiquidity(), c.isAlignedWithVWAP(), c.isInKillzone(),
                c.c1Open(), c.c1Close(), c.c1High(), c.c1Low(), c.c1Volume(),
                c.c2Open(), c.c2Close(), c.c2High(), c.c2Low(), c.c2Volume(),
                c.c3Open(), c.c3Close(), c.c3High(), c.c3Low(), c.c3Volume()
        );

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", props.getModel(),
                    "max_tokens", props.getMaxTokens(),
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            String responseBody = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String responseText = root.path("content").get(0).path("text").asText().trim();

            if (responseText.startsWith("```json")) {
                responseText = responseText.replace("```json", "").replace("```", "").trim();
            }

            return objectMapper.readValue(responseText, ClaudeVerdict.class);

        } catch (Exception e) {
            log.error("🚨 Error consultando a Claude para {}: {}", c.symbol(), e.getMessage());
            return new ClaudeVerdict(false, "ERROR", 0.0, "Error en API IA: " + e.getMessage());
        }
    }
}