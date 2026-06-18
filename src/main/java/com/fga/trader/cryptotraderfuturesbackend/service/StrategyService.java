package com.fga.trader.cryptotraderfuturesbackend.service;

import com.fga.trader.cryptotraderfuturesbackend.config.RiskProperties;
import com.fga.trader.cryptotraderfuturesbackend.ports.service.StrategyServicePort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.BinanceSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.ClaudeValidationPort;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.KlineSpyPort;
import com.fga.trader.cryptotraderfuturesbackend.records.FVGValidationResult;
import com.fga.trader.cryptotraderfuturesbackend.strategies.FairValueGapStrategy;
import com.fga.tradermodel.dto.OperationParametersDto;
import com.fga.tradermodel.dto.Trend;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class StrategyService implements StrategyServicePort {

    private final KlineSpyPort klineSpyPort;
    private final BinanceSpyPort binanceSpyPort;
    private final ClaudeValidationPort claudeValidationPort;
    private final RiskProperties riskProperties;

    @Override
    public OperationParametersDto executeStrategy(String symbol, String temporality, Trend trend) {
        try {
            return executeFairValueGapStrategy(symbol, temporality, trend);
        } catch (Exception e) {
            log.error("❌ [StrategyService] Error inesperado ejecutando estrategia para {} {} ({}): {}",
                    symbol, temporality, trend, e.getMessage(), e);
            return null;
        }
    }

    private OperationParametersDto executeFairValueGapStrategy(String symbol, String temporality, Trend trend) {
        FairValueGapStrategy strategy = new FairValueGapStrategy(
                klineSpyPort, claudeValidationPort, riskProperties, symbol, temporality, trend);

        // Detecta el patrón localmente y, si pasa los filtros, lo confirma con Claude.
        // Devuelve null si no hay patrón o si Claude lo clasifica como ruido.
        FVGValidationResult result = strategy.isPatternPresent();

        if (result == null) return null;

        log.info("🎯 [StrategyService] FVG OPERABLE {} {} en temporalidad de {} | Conf: {} | Lev: {}x | Entry: {} | SL: {} | TP: {} | LiqPrice: {} | Margen: {} USDT | Notional: {} USDT",
                symbol, trend, temporality, result.confidence(), result.leverage(), result.entryPrice(),
                result.stopLoss(), result.takeProfit(), result.liquidationPrice(),
                result.marginUsdt(), result.notionalUsdt());

        return mapToOperationParameters(symbol, temporality, result);
    }

    private OperationParametersDto mapToOperationParameters(String symbol, String temporality, FVGValidationResult result) {
        // TODO: ajustar al constructor/setters reales de OperationParametersDto.
        // Ejemplo orientativo (descomenta y adapta a tus campos):
        //
         return OperationParametersDto.builder()
                 .symbol(symbol)
                 .temporality(temporality)
                 .trend(result.trend())
                 .entryPrice(result.entryPrice())
                 .stopLoss(result.stopLoss())
                 .takeProfit(result.takeProfit())
                 .leverage(result.leverage())
                 .marginUsdt(result.marginUsdt())
                 .notionalUsdt(result.notionalUsdt())
                 .reasoning(result.reasoning())
                 .build();
    }
}