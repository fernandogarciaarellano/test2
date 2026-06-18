package com.fga.trader.cryptotraderfuturesbackend.ports.spy;

import com.fga.tradermodel.dto.OperationParametersDto;

public interface BinanceOrderSpyPort {
    String placeOrder(OperationParametersDto params);
    String getOrderStatus(String symbol, Long orderId);
    String cancelOrder(String symbol, Long orderId);
    double getAvailableBalance(String asset);

    boolean hasOpenPosition(String symbol);

    double getMarkPrice(String symbol);
    String placeStopLoss(String symbol, String exitSide, double stopPrice, String quantity);
    void cancelAllAlgoOrders(String symbol);
}
