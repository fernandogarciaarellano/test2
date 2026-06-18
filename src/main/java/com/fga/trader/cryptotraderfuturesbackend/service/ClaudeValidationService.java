package com.fga.trader.cryptotraderfuturesbackend.adapters.spy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fga.trader.cryptotraderfuturesbackend.config.ClaudeProperties;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.ClaudeValidationPort;
import com.fga.trader.cryptotraderfuturesbackend.records.ClaudeVerdict;
import com.fga.trader.cryptotraderfuturesbackend.records.FVGCandidate;
import com.fga.tradermodel.dto.Trend;
import lombok.extern.log4j.Log4j2;
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
    // ObjectMapper de Jackson 2 (com.fasterxml) — lo usamos NOSOTROS, no el converter de Spring
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
    public ClaudeVerdict validateFvg(FVGCandidate candidate) {
        try {
            String prompt = buildPrompt(candidate);

            Map<String, Object> requestBody = Map.of(
                    "model", props.getModel(),
                    "max_tokens", props.getMaxTokens(),
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            // 🔑 Recibimos la respuesta como String para evitar el conflicto Jackson 2 / Jackson 3
            String rawResponse = restClient.post()
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // Parseamos nosotros con nuestro ObjectMapper de Jackson 2
            JsonNode root = objectMapper.readTree(rawResponse);
            String text = extractText(root);
            String cleanJson = stripFences(text);

            return objectMapper.readValue(cleanJson, ClaudeVerdict.class);

        } catch (Exception e) {
            log.error("❌ Error validando FVG con Claude para {}: {}", candidate.symbol(), e.getMessage(), e);
            return new ClaudeVerdict(false, "NOISE", 0.0, "Error en validación: " + e.getMessage());
        }
    }

    private String extractText(JsonNode response) {
        JsonNode content = response.path("content");
        StringBuilder sb = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText());
                }
            }
        }
        return sb.toString();
    }

    private String stripFences(String text) {
        return text.replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
    }

    private String buildPrompt(FVGCandidate c) {
        String direction = c.trend() == Trend.ALCISTA ? "ALCISTA (LONG)" : "BAJISTA (SHORT)";

        return """
                Eres un analista experto en trading de futuros de criptomonedas, especializado en \
                la estrategia Fair Value Gap (FVG) / imbalance de Smart Money Concepts.

                Tu única tarea es clasificar si el siguiente patrón de 3 velas es un FVG VÁLIDO o RUIDO \
                de mercado. NO calcules apalancamiento ni precios; eso lo hace el sistema.

                ## CONTEXTO DEL PATRÓN
                Símbolo: %s
                Temporalidad: %s
                Dirección esperada: %s
                Tamaño del gap detectado: %.8f
                Volumen promedio (20 velas): %.4f

                ## DATOS DE LAS 3 VELAS (C1=anterior, C2=impulso, C3=confirmación)
                C1 -> Open: %.8f | Close: %.8f | High: %.8f | Low: %.8f | Vol: %.4f
                C2 -> Open: %.8f | Close: %.8f | High: %.8f | Low: %.8f | Vol: %.4f
                C3 -> Open: %.8f | Close: %.8f | High: %.8f | Low: %.8f | Vol: %.4f

                ## CRITERIOS
                FVG ALCISTA válido: el Low de C3 por encima del High de C1 (hueco real). C2 vela de \
                impulso alcista fuerte con volumen elevado.
                FVG BAJISTA válido: el High de C3 por debajo del Low de C1. C2 vela de impulso bajista \
                fuerte con volumen elevado.
                Es RUIDO si: el cuerpo de C2 es débil, el volumen no respalda el movimiento, el gap es \
                insignificante respecto a la volatilidad, o las mechas indican rechazo/indecisión.

                ## FORMATO DE RESPUESTA
                Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional ni markdown:
                {
                  "isValid": boolean,
                  "classification": "VALID_FVG" | "NOISE",
                  "confidence": number (0.0 a 1.0),
                  "reasoning": "explicación breve en español"
                }
                """.formatted(
                c.symbol(), c.temporality(), direction, c.gapSize(), c.avgVolume(),
                c.c1Open(), c.c1Close(), c.c1High(), c.c1Low(), c.c1Volume(),
                c.c2Open(), c.c2Close(), c.c2High(), c.c2Low(), c.c2Volume(),
                c.c3Open(), c.c3Close(), c.c3High(), c.c3Low(), c.c3Volume()
        );
    }
}