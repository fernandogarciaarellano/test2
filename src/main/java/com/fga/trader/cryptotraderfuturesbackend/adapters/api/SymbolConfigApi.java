package com.fga.trader.cryptotraderfuturesbackend.adapters.api;

import java.util.List;

import com.fga.tradermodel.dto.SymbolConfig;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SymbolConfigApi {

    String BASE_PATH = "api-persistence/v1/symbolConfig";

    @POST(BASE_PATH + "/saveAll")
    Call<List<SymbolConfig>> saveAll(@Body List<SymbolConfig> symbols);

    @GET(BASE_PATH + "/getAll")
    Call<List<SymbolConfig>> getAll();

    @GET(BASE_PATH + "/deleteAll")
    Call<Void> deleteAll();

    @GET(BASE_PATH + "/findById/{symbol}")
    Call<SymbolConfig> findById(@Path("symbol") String symbol);
}