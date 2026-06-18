package com.fga.trader.cryptotraderfuturesbackend.adapters.jdbc;

import com.fga.trader.cryptotraderfuturesbackend.adapters.api.*;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.KlineSpyPort;
import com.fga.tradermodel.dto.KlineDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.util.List;

@Service
@Log4j2
@RequiredArgsConstructor
public class KlineJdbcAdapter implements KlineSpyPort {

    private final Kline1dApi kline1dApi;
    private final Kline4hApi kline4hApi;
    private final Kline1hApi kline1hApi;
    private final Kline15mApi kline15mApi;
    private final Kline5mApi kline5mApi;

    // 🟢 1d =================================================================

    @Override
    public List<KlineDto> saveAll1d(List<KlineDto> klines) {
        try {
            Response<List<KlineDto>> response = kline1dApi.saveAll(klines).execute();
            return response.isSuccessful() ? response.body() : List.of();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.saveAll1d", e);
            return List.of();
        }
    }

    @Override
    public KlineDto save1d(KlineDto kline) {
        try {
            Response<KlineDto> response = kline1dApi.save(kline).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.save1d", e);
            return null;
        }
    }

    @Override
    public void deleteById1d(Long id) {
        try {
            kline1dApi.deleteById(id).execute();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.deleteById1d", e);
        }
    }

    @Override
    public KlineDto getOldest1d(String symbol) {
        try {
            Response<KlineDto> response = kline1dApi.getOldest(symbol).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getOldest1d", e);
            return null;
        }
    }

    @Override
    public List<KlineDto> getAllBySymbol1d(String symbol) {
        try {
            Response<List<KlineDto>> response = kline1dApi.getAllBySymbol(symbol).execute();
            return response.isSuccessful() ? response.body() : List.of();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getAllBySymbol1d", e);
            return List.of();
        }
    }

    @Override
    public KlineDto getNewest1d(String symbol) {
        try {
            Response<KlineDto> response = kline1dApi.getNewest(symbol).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getNewest1d", e);
            return null;
        }
    }

    @Override
    public void deleteOldestN1d(String symbol, int limit) {
        try {
            kline1dApi.deleteOldestN(symbol, limit).execute();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.deleteOldestN1d", e);
        }
    }

    // 🟢 4h =================================================================

    @Override
    public List<KlineDto> saveAll4h(List<KlineDto> klines) {
        try {
            Response<List<KlineDto>> response = kline4hApi.saveAll(klines).execute();
            return response.isSuccessful() ? response.body() : List.of();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.saveAll4h", e);
            return List.of();
        }
    }

    @Override
    public KlineDto save4h(KlineDto kline) {
        try {
            Response<KlineDto> response = kline4hApi.save(kline).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.save4h", e);
            return null;
        }
    }

    @Override
    public void deleteById4h(Long id) {
        try {
            kline4hApi.deleteById(id).execute();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.deleteById4h", e);
        }
    }

    @Override
    public KlineDto getOldest4h(String symbol) {
        try {
            Response<KlineDto> response = kline4hApi.getOldest(symbol).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getOldest4h", e);
            return null;
        }
    }

    @Override
    public List<KlineDto> getAllBySymbol4h(String symbol) {
        try {
            Response<List<KlineDto>> response = kline4hApi.getAllBySymbol(symbol).execute();
            return response.isSuccessful() ? response.body() : List.of();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getAllBySymbol4h", e);
            return List.of();
        }
    }

    @Override
    public KlineDto getNewest4h(String symbol) {
        try {
            Response<KlineDto> response = kline4hApi.getNewest(symbol).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getNewest4h", e);
            return null;
        }
    }

    @Override
    public void deleteOldestN4h(String symbol, int limit) {
        try {
            kline4hApi.deleteOldestN(symbol, limit).execute();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.deleteOldestN4h", e);
        }
    }

    // 🟢 1h =================================================================

    @Override
    public List<KlineDto> saveAll1h(List<KlineDto> klines) {
        try {
            Response<List<KlineDto>> response = kline1hApi.saveAll(klines).execute();
            return response.isSuccessful() ? response.body() : List.of();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.saveAll1h", e);
            return List.of();
        }
    }

    @Override
    public KlineDto save1h(KlineDto kline) {
        try {
            Response<KlineDto> response = kline1hApi.save(kline).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.save1h", e);
            return null;
        }
    }

    @Override
    public void deleteById1h(Long id) {
        try {
            kline1hApi.deleteById(id).execute();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.deleteById1h", e);
        }
    }

    @Override
    public KlineDto getOldest1h(String symbol) {
        try {
            Response<KlineDto> response = kline1hApi.getOldest(symbol).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getOldest1h", e);
            return null;
        }
    }

    @Override
    public List<KlineDto> getAllBySymbol1h(String symbol) {
        try {
            Response<List<KlineDto>> response = kline1hApi.getAllBySymbol(symbol).execute();
            return response.isSuccessful() ? response.body() : List.of();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getAllBySymbol1h", e);
            return List.of();
        }
    }

    @Override
    public KlineDto getNewest1h(String symbol) {
        try {
            Response<KlineDto> response = kline1hApi.getNewest(symbol).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getNewest1h", e);
            return null;
        }
    }

    @Override
    public void deleteOldestN1h(String symbol, int limit) {
        try {
            kline1hApi.deleteOldestN(symbol, limit).execute();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.deleteOldestN1h", e);
        }
    }

    // 🟢 15m ================================================================

    @Override
    public List<KlineDto> saveAll15m(List<KlineDto> klines) {
        try {
            Response<List<KlineDto>> response = kline15mApi.saveAll(klines).execute();
            return response.isSuccessful() ? response.body() : List.of();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.saveAll15m", e);
            return List.of();
        }
    }

    @Override
    public KlineDto save15m(KlineDto kline) {
        try {
            Response<KlineDto> response = kline15mApi.save(kline).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.save15m", e);
            return null;
        }
    }

    @Override
    public void deleteById15m(Long id) {
        try {
            kline15mApi.deleteById(id).execute();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.deleteById15m", e);
        }
    }

    @Override
    public KlineDto getOldest15m(String symbol) {
        try {
            Response<KlineDto> response = kline15mApi.getOldest(symbol).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getOldest15m", e);
            return null;
        }
    }

    @Override
    public List<KlineDto> getAllBySymbol15m(String symbol) {
        try {
            Response<List<KlineDto>> response = kline15mApi.getAllBySymbol(symbol).execute();
            return response.isSuccessful() ? response.body() : List.of();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getAllBySymbol15m", e);
            return List.of();
        }
    }

    @Override
    public KlineDto getNewest15m(String symbol) {
        try {
            Response<KlineDto> response = kline15mApi.getNewest(symbol).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getNewest15m", e);
            return null;
        }
    }

    @Override
    public void deleteOldestN15m(String symbol, int limit) {
        try {
            kline15mApi.deleteOldestN(symbol, limit).execute();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.deleteOldestN15m", e);
        }
    }

    // 🟢 5m =================================================================

    @Override
    public List<KlineDto> saveAll5m(List<KlineDto> klines) {
        try {
            Response<List<KlineDto>> response = kline5mApi.saveAll(klines).execute();
            return response.isSuccessful() ? response.body() : List.of();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.saveAll5m", e);
            return List.of();
        }
    }

    @Override
    public KlineDto save5m(KlineDto kline) {
        try {
            Response<KlineDto> response = kline5mApi.save(kline).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.save5m", e);
            return null;
        }
    }

    @Override
    public void deleteById5m(Long id) {
        try {
            kline5mApi.deleteById(id).execute();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.deleteById5m", e);
        }
    }

    @Override
    public KlineDto getOldest5m(String symbol) {
        try {
            Response<KlineDto> response = kline5mApi.getOldest(symbol).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getOldest5m", e);
            return null;
        }
    }

    @Override
    public List<KlineDto> getAllBySymbol5m(String symbol) {
        try {
            Response<List<KlineDto>> response = kline5mApi.getAllBySymbol(symbol).execute();
            return response.isSuccessful() ? response.body() : List.of();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getAllBySymbol5m", e);
            return List.of();
        }
    }

    @Override
    public KlineDto getNewest5m(String symbol) {
        try {
            Response<KlineDto> response = kline5mApi.getNewest(symbol).execute();
            return response.isSuccessful() ? response.body() : null;
        } catch (Exception e) {
            log.error("error KlineClientAdapter.getNewest5m", e);
            return null;
        }
    }

    @Override
    public void deleteOldestN5m(String symbol, int limit) {
        try {
            kline5mApi.deleteOldestN(symbol, limit).execute();
        } catch (Exception e) {
            log.error("error KlineClientAdapter.deleteOldestN5m", e);
        }
    }
}