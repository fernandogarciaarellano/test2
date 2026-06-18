package com.fga.trader.cryptotraderfuturesbackend.adapters.api;

import com.fga.tradermodel.dto.OrderDto;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface OrderApi {

    String BASE_PATH = "api-persistence/v1/order";

    @POST(BASE_PATH + "/save")
    Call<OrderDto> save(@Body OrderDto order);

}
