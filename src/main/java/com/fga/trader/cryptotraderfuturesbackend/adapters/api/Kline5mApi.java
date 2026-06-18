package com.fga.trader.cryptotraderfuturesbackend.adapters.api;

import com.fga.tradermodel.dto.KlineDto;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface Kline5mApi {

    String BASE_PATH = "api-persistence/v1/kline5m";

    @POST(BASE_PATH + "/saveAll")
    Call<List<KlineDto>> saveAll(@Body List<KlineDto> klines);

    @POST(BASE_PATH + "/save")
    Call<KlineDto> save(@Body KlineDto kline);

    @DELETE(BASE_PATH + "/{id}")
    Call<Void> deleteById(@Path("id") Long id);

    @GET(BASE_PATH + "/oldest/{symbol}")
    Call<KlineDto> getOldest(@Path("symbol") String symbol);

    @GET(BASE_PATH + "/all/{symbol}")
    Call<List<KlineDto>> getAllBySymbol(@Path("symbol") String symbol);

    @GET(BASE_PATH + "/newest/{symbol}")
    Call<KlineDto> getNewest(@Path("symbol") String symbol);

    @DELETE(BASE_PATH + "/oldest/{symbol}/{limit}")
    Call<Void> deleteOldestN(@Path("symbol") String symbol, @Path("limit") int limit);
}