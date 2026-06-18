package com.fga.trader.cryptotraderfuturesbackend.utils;

import com.fga.trader.cryptotraderfuturesbackend.records.PositionDetails;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PositionCalculatorUtil {

    /**
     * Calcula el tamaño de la posición y el apalancamiento para que, si el precio
     * viaja desde el precio actual hasta el precio límite (salida),
     * la pérdida sea exactamente el capital en riesgo.
     *
     * @param currentPrice   El precio actual de entrada.
     * @param limitPrice     El precio de salida (El más bajo de la vela para Long, o más alto para Short).
     * @param riskAmountUsdt La cantidad de dinero (USDT) que estás dispuesto a perder si se toca el limitPrice.
     * @return PositionDetails con la cantidad a comprar y el apalancamiento a configurar.
     */
    public static PositionDetails calculatePosition(double currentPrice, double limitPrice, double riskAmountUsdt) {

        // 1. Calculamos la distancia absoluta entre el precio de entrada y el punto de invalidación
        double distance = Math.abs(currentPrice - limitPrice);

        if (distance == 0) {
            log.error("La distancia entre el precio actual y el límite es 0. Operación rechazada.");
            throw new IllegalArgumentException("El precio límite no puede ser igual al precio actual.");
        }

        // 2. TAMAÑO DE LA POSICIÓN (Quantity)
        // ¿Cuántas monedas necesito comprar para que, si el precio cae 'X' dólares, yo pierda exactamente mi riesgo?
        double quantity = riskAmountUsdt / distance;

        // 3. VALOR NOMINAL (Notional Value)
        // Es el tamaño total de la operación como si no usaras apalancamiento
        double notionalValue = quantity * currentPrice;

        // 4. APALANCAMIENTO (Leverage)
        // El apalancamiento máximo teórico es = Precio Actual / Distancia.
        // Ejemplo: Entras a 100, sales a 90. Distancia = 10 (10%). Max Apalancamiento = 100/10 = 10x.
        double rawLeverage = currentPrice / distance;

        // IMPORTANTE: Binance tiene un "Margen de Mantenimiento". Si usas el apalancamiento máximo exacto,
        // Binance te liquidará UN POCO ANTES de que el precio llegue a tu limitPrice.
        // Para asegurar que la operación sobreviva hasta tu limitPrice, reducimos el apalancamiento un 15% por seguridad.
        double safeLeverage = rawLeverage * 0.85;

        // Redondeamos el apalancamiento hacia abajo (floor) y lo limitamos entre 1x y un máximo prudente (ej. 50x)
        int finalLeverage = (int) Math.max(1, Math.floor(safeLeverage));

        // Límite de seguridad institucional (Puedes ajustarlo si operas altcoins volátiles que solo permiten 20x)
        if (finalLeverage > 50) {
            log.warn("El apalancamiento calculado ({}x) supera el máximo seguro. Reduciendo a 50x.", finalLeverage);
            finalLeverage = 50;
        }

        log.debug("Cálculo de posición: Riesgo=${}, Entrada=${}, Salida=${}. Cantidad={}, Apalancamiento={}x",
                riskAmountUsdt, currentPrice, limitPrice, quantity, finalLeverage);

        return new PositionDetails(quantity, finalLeverage, notionalValue, riskAmountUsdt);
    }
}