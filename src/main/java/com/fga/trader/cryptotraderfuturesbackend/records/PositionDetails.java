package com.fga.trader.cryptotraderfuturesbackend.records;

/**
 * Record nativo de Java 21 para encapsular los resultados del cálculo.
 */
public record PositionDetails(
        double quantity,       // Tamaño de la orden (Monedas a comprar/vender, ej. 0.015 BTC)
        int leverage,          // Apalancamiento requerido (ej. 15x)
        double notionalValue,  // Valor total de la posición apalancada en USDT
        double riskAmountUsdt  // Capital real en riesgo (Margen)
) {}