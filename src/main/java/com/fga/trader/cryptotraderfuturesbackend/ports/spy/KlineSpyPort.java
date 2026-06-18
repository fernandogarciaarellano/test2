package com.fga.trader.cryptotraderfuturesbackend.ports.spy;

import com.fga.tradermodel.dto.KlineDto;
import java.util.List;

public interface KlineSpyPort {

    // --- 1d ---
    List<KlineDto> saveAll1d(List<KlineDto> klines);
    KlineDto save1d(KlineDto kline);
    void deleteById1d(Long id);
    KlineDto getOldest1d(String symbol);
    List<KlineDto> getAllBySymbol1d(String symbol);
    KlineDto getNewest1d(String symbol);
    void deleteOldestN1d(String symbol, int limit);

    // --- 4h ---
    List<KlineDto> saveAll4h(List<KlineDto> klines);
    KlineDto save4h(KlineDto kline);
    void deleteById4h(Long id);
    KlineDto getOldest4h(String symbol);
    List<KlineDto> getAllBySymbol4h(String symbol);
    KlineDto getNewest4h(String symbol);
    void deleteOldestN4h(String symbol, int limit);

    // --- 1h ---
    List<KlineDto> saveAll1h(List<KlineDto> klines);
    KlineDto save1h(KlineDto kline);
    void deleteById1h(Long id);
    KlineDto getOldest1h(String symbol);
    List<KlineDto> getAllBySymbol1h(String symbol);
    KlineDto getNewest1h(String symbol);
    void deleteOldestN1h(String symbol, int limit);

    // --- 15m ---
    List<KlineDto> saveAll15m(List<KlineDto> klines);
    KlineDto save15m(KlineDto kline);
    void deleteById15m(Long id);
    KlineDto getOldest15m(String symbol);
    List<KlineDto> getAllBySymbol15m(String symbol);
    KlineDto getNewest15m(String symbol);
    void deleteOldestN15m(String symbol, int limit);

    // --- 5m ---
    List<KlineDto> saveAll5m(List<KlineDto> klines);
    KlineDto save5m(KlineDto kline);
    void deleteById5m(Long id);
    KlineDto getOldest5m(String symbol);
    List<KlineDto> getAllBySymbol5m(String symbol);
    KlineDto getNewest5m(String symbol);
    void deleteOldestN5m(String symbol, int limit);
}