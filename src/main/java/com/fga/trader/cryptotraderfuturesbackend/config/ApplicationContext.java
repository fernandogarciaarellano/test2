package com.fga.trader.cryptotraderfuturesbackend.config;

import com.binance.connector.client.common.configuration.ClientConfiguration;
import com.binance.connector.client.common.configuration.SignatureConfiguration;
import com.binance.connector.client.spot.rest.SpotRestApiUtil;
import com.binance.connector.client.spot.rest.api.SpotRestApi;
import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fga.trader.cryptotraderfuturesbackend.adapters.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
public class ApplicationContext {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }

    @Bean
    public SpotRestApi spotRestApi(@Value("${binance.api.key}") String key, @Value("${binance.api.secret}") String secret) {

        ClientConfiguration clientConfiguration = SpotRestApiUtil.getClientConfiguration();
        SignatureConfiguration signatureConfiguration = new SignatureConfiguration();
        signatureConfiguration.setApiKey(key);
        signatureConfiguration.setSecretKey(secret);
        clientConfiguration.setSignatureConfiguration(signatureConfiguration);

        return new SpotRestApi(clientConfiguration);
    }

    @Bean
    public SymbolConfigApi symbolConfigApi(@Value("${persistence.sql.baseurl}") String urlPersistence) {
        return new ApiFactory<SymbolConfigApi>(urlPersistence, SymbolConfigApi.class).getApi();
    }

    @Bean
    public Kline1dApi kline1dApi(@Value("${persistence.sql.baseurl}") String urlPersistence) {
        return new ApiFactory<Kline1dApi>(urlPersistence, Kline1dApi.class).getApi();
    }

    @Bean
    public Kline4hApi kline4hApi(@Value("${persistence.sql.baseurl}") String urlPersistence) {
        return new ApiFactory<Kline4hApi>(urlPersistence, Kline4hApi.class).getApi();
    }

    @Bean
    public Kline1hApi kline1hApi(@Value("${persistence.sql.baseurl}") String urlPersistence) {
        return new ApiFactory<Kline1hApi>(urlPersistence, Kline1hApi.class).getApi();
    }

    @Bean
    public Kline15mApi kline15mApi(@Value("${persistence.sql.baseurl}") String urlPersistence) {
        return new ApiFactory<Kline15mApi>(urlPersistence, Kline15mApi.class).getApi();
    }

    @Bean
    public Kline5mApi kline5mApi(@Value("${persistence.sql.baseurl}") String urlPersistence) {
        return new ApiFactory<Kline5mApi>(urlPersistence, Kline5mApi.class).getApi();
    }

    @Bean
    public OrderApi orderApi(@Value("${persistence.sql.baseurl}") String urlPersistence) {
        return new ApiFactory<OrderApi>(urlPersistence, OrderApi.class).getApi();
    }

    @Bean
    public UMFuturesClientImpl umFuturesClient(@Value("${binance.api.key}") String apikey, @Value("${binance.api.secret}") String secretKey) {
        String testnetBaseUrl = "https://testnet.binancefuture.com";
        return new UMFuturesClientImpl(apikey, secretKey, testnetBaseUrl);
    }

}
