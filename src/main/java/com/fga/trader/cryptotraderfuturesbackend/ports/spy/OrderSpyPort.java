package com.fga.trader.cryptotraderfuturesbackend.ports.spy;

import com.fga.tradermodel.dto.OrderDto;

public interface OrderSpyPort {

    OrderDto save(OrderDto orderDto);

}
