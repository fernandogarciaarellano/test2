package com.fga.trader.cryptotraderfuturesbackend.adapters.jdbc;

import com.fga.trader.cryptotraderfuturesbackend.adapters.api.SymbolConfigApi;
import com.fga.trader.cryptotraderfuturesbackend.ports.spy.SymbolConfigSpyPort;
import com.fga.tradermodel.dto.SymbolConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;

import java.util.List;

@Service
@Log4j2
@RequiredArgsConstructor
public class SymbolConfigJdbcAdapter implements SymbolConfigSpyPort {

    private final SymbolConfigApi symbolConfigApi;

    @Override
    public List<SymbolConfig> saveAll(List<SymbolConfig> symbols) {

        try {
            Response<List<SymbolConfig>> response = symbolConfigApi.saveAll(symbols).execute();
            if (response.isSuccessful()) {
                return response.body();
            }
            return List.of();
        } catch (Exception e) {
            log.error("Error SymbolConfigJdbcAdapter.saveAll", e);
            return List.of();
        }

    }

    @Override
    public List<SymbolConfig> getAll() {
        try {
            Response<List<SymbolConfig>> response = symbolConfigApi.getAll().execute();
            if (response.isSuccessful()) {
                return response.body();
            }
            return List.of();
        } catch (Exception e) {
            log.error("Error SymbolConfigJdbcAdapter.getAll", e);
            return List.of();
        }
    }

    @Override
    public void deleteAll() {
        try {
            symbolConfigApi.deleteAll().execute();
        } catch (Exception e) {
            log.error("Error SymbolConfigJdbcAdapter.deleteAll", e);
        }
    }

    @Override
    public SymbolConfig findById(String symbol) {
        try {
            Response<SymbolConfig> response = symbolConfigApi.findById(symbol).execute();
            if (response.isSuccessful()) {
                return response.body();
            }
            return null;
        } catch (Exception e) {
            log.error("Error SymbolConfigJdbcAdapter.findById", e);
            return null;
        }
    }
}
