package com.fga.trader.cryptotraderfuturesbackend.adapters.jdbc;

import com.fga.trader.cryptotraderfuturesbackend.adapters.api.OrderApi;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.OrderSpyPort;
import com.fga.tradermodel.dto.OrderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import retrofit2.Response;

@Service
@Log4j2
@RequiredArgsConstructor
public class OrderJdbcAdapter implements OrderSpyPort {

    private final OrderApi orderApi;

    @Override
    public OrderDto save(OrderDto orderDto) {
        try {
            Response<OrderDto> response = orderApi.save(orderDto).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error OrderJdbcAdapter.save", e);
            return null;
        }
    }
}
