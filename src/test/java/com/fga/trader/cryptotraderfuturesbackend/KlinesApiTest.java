package com.fga.trader.cryptotraderfuturesbackend;

import com.fga.trader.cryptotraderfuturesbackend.adapters.api.*;
import com.fga.tradermodel.dto.KlineDto;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class KlinesApiTest {

    @Autowired
    private Kline15mApi kline15mApi;

    @Autowired
    private Kline5mApi kline5mApi;

    @Autowired
    private Kline1hApi kline1hApi;

    @Autowired
    private Kline4hApi kline4hApi;

    @Autowired
    private Kline1dApi kline1dApi;

    private static List<Long> ids = new ArrayList<>();
    static {
        ids.add(1l);
        ids.add(2l);
        ids.add(3l);
    }
    @Test
    @Order(1)
    @DisplayName("Prueba End-to-End: Guardar y Validar Kline 5m")
    void testSaveAndVerify5m() throws Exception {

        // 1. Crear dato de prueba
        KlineDto kline = new KlineDto();
        kline.setOpenTime(ids.get(0));
        kline.setCloseTime(1776186899999L);
        kline.setSymbol("ETHUSDT");
        kline.setOpenPrice(2334.0);
        kline.setClosePrice(2338.58);
        kline.setMaxPrice(2340.17);
        kline.setMinPrice(2329.41);

        // 2. Ejecutar la llamada a través del Bean de Retrofit (pasa por el Controller a la DB)
        Response<KlineDto> response = kline5mApi.save(kline).execute();

        // 3. Validaciones
        assertTrue(response.isSuccessful(), "La petición al Controller falló");
        assertNotNull(response.body());
        assertEquals("ETHUSDT", response.body().getSymbol());
        System.out.println("✅ Dato insertado en DB via Endpoint: " + response.body().getOpenTime());
    }



    @Test
    @Order(2)
    @DisplayName("Prueba End-to-End: Guardar Lista Masiva 5m")
    void testSaveAll5m() throws Exception {

        KlineDto k1 = createSampleKline1d(ids.get(1), "BTCUSDT");
        KlineDto k2 = createSampleKline1d(ids.get(2), "BTCUSDT");

        Response<List<KlineDto>> response = kline5mApi.saveAll(List.of(k1, k2)).execute();

        assertTrue(response.isSuccessful());
        assertEquals(2, response.body().size());
        System.out.println("✅ Lista masiva persistida exitosamente.");
    }

    @Test
    @Order(3)
    @DisplayName("Prueba End-to-End: Guardar y Validar Kline 15m")
    void testSaveAndVerify15m() throws Exception {


        // 1. Crear dato de prueba
        KlineDto kline = new KlineDto();
        kline.setOpenTime(ids.get(0));
        kline.setCloseTime(1776186899999L);
        kline.setSymbol("ETHUSDT");
        kline.setOpenPrice(2334.0);
        kline.setClosePrice(2338.58);
        kline.setMaxPrice(2340.17);
        kline.setMinPrice(2329.41);

        // 2. Ejecutar la llamada a través del Bean de Retrofit (pasa por el Controller a la DB)
        Response<KlineDto> response = kline15mApi.save(kline).execute();

        // 3. Validaciones
        assertTrue(response.isSuccessful(), "La petición al Controller falló");
        assertNotNull(response.body());
        assertEquals("ETHUSDT", response.body().getSymbol());
        System.out.println("✅ Dato insertado en DB via Endpoint: " + response.body().getOpenTime());
    }



    @Test
    @Order(4)
    @DisplayName("Prueba End-to-End: Guardar Lista Masiva 15m")
    void testSaveAll15m() throws Exception {

        KlineDto k1 = createSampleKline1d(ids.get(1), "BTCUSDT");
        KlineDto k2 = createSampleKline1d(ids.get(2), "BTCUSDT");

        Response<List<KlineDto>> response = kline15mApi.saveAll(List.of(k1, k2)).execute();

        assertTrue(response.isSuccessful());
        assertEquals(2, response.body().size());
        System.out.println("✅ Lista masiva persistida exitosamente.");
    }

    @Test
    @Order(5)
    @DisplayName("Prueba End-to-End: Guardar y Validar Kline 1h")
    void testSaveAndVerify1h() throws Exception {

        // 1. Crear dato de prueba
        KlineDto kline = new KlineDto();
        kline.setOpenTime(ids.get(0));
        kline.setCloseTime(1776186899999L);
        kline.setSymbol("ETHUSDT");
        kline.setOpenPrice(2334.0);
        kline.setClosePrice(2338.58);
        kline.setMaxPrice(2340.17);
        kline.setMinPrice(2329.41);

        // 2. Ejecutar la llamada a través del Bean de Retrofit (pasa por el Controller a la DB)
        Response<KlineDto> response = kline1hApi.save(kline).execute();

        // 3. Validaciones
        assertTrue(response.isSuccessful(), "La petición al Controller falló");
        assertNotNull(response.body());
        assertEquals("ETHUSDT", response.body().getSymbol());
        System.out.println("✅ Dato insertado en DB via Endpoint: " + response.body().getOpenTime());
    }



    @Test
    @Order(6)
    @DisplayName("Prueba End-to-End: Guardar Lista Masiva 1h")
    void testSaveAll1h() throws Exception {

        KlineDto k1 = createSampleKline1d(ids.get(1), "BTCUSDT");
        KlineDto k2 = createSampleKline1d(ids.get(2), "BTCUSDT");

        Response<List<KlineDto>> response = kline1hApi.saveAll(List.of(k1, k2)).execute();

        assertTrue(response.isSuccessful());
        assertEquals(2, response.body().size());
        System.out.println("✅ Lista masiva persistida exitosamente.");
    }

    @Test
    @Order(7)
    @DisplayName("Prueba End-to-End: Guardar y Validar Kline 4h")
    void testSaveAndVerify4h() throws Exception {

        // 1. Crear dato de prueba
        KlineDto kline = new KlineDto();
        kline.setOpenTime(ids.get(0));
        kline.setCloseTime(1776186899999L);
        kline.setSymbol("ETHUSDT");
        kline.setOpenPrice(2334.0);
        kline.setClosePrice(2338.58);
        kline.setMaxPrice(2340.17);
        kline.setMinPrice(2329.41);

        // 2. Ejecutar la llamada a través del Bean de Retrofit (pasa por el Controller a la DB)
        Response<KlineDto> response = kline4hApi.save(kline).execute();

        // 3. Validaciones
        assertTrue(response.isSuccessful(), "La petición al Controller falló");
        assertNotNull(response.body());
        assertEquals("ETHUSDT", response.body().getSymbol());
        System.out.println("✅ Dato insertado en DB via Endpoint: " + response.body().getOpenTime());
    }



    @Test
    @Order(8)
    @DisplayName("Prueba End-to-End: Guardar Lista Masiva 4h")
    void testSaveAll4h() throws Exception {

        KlineDto k1 = createSampleKline1d(ids.get(1), "BTCUSDT");
        KlineDto k2 = createSampleKline1d(ids.get(2), "BTCUSDT");

        Response<List<KlineDto>> response = kline4hApi.saveAll(List.of(k1, k2)).execute();

        assertTrue(response.isSuccessful());
        assertEquals(2, response.body().size());
        System.out.println("✅ Lista masiva persistida exitosamente.");
    }

    @Test
    @Order(9)
    @DisplayName("Prueba End-to-End: Guardar y Validar Kline 1d")
    void testSaveAndVerify1d() throws Exception {

        // 1. Crear dato de prueba
        KlineDto kline = new KlineDto();
        kline.setOpenTime(ids.get(0));
        kline.setCloseTime(1776186899999L);
        kline.setSymbol("ETHUSDT");
        kline.setOpenPrice(2334.0);
        kline.setClosePrice(2338.58);
        kline.setMaxPrice(2340.17);
        kline.setMinPrice(2329.41);

        // 2. Ejecutar la llamada a través del Bean de Retrofit (pasa por el Controller a la DB)
        Response<KlineDto> response = kline1dApi.save(kline).execute();

        // 3. Validaciones
        assertTrue(response.isSuccessful(), "La petición al Controller falló");
        assertNotNull(response.body());
        assertEquals("ETHUSDT", response.body().getSymbol());
        System.out.println("✅ Dato insertado en DB via Endpoint: " + response.body().getOpenTime());
    }



    @Test
    @Order(10)
    @DisplayName("Prueba End-to-End: Guardar Lista Masiva 1d")
    void testSaveAll1d() throws Exception {

        KlineDto k1 = createSampleKline1d(ids.get(1), "BTCUSDT");
        KlineDto k2 = createSampleKline1d(ids.get(2), "BTCUSDT");

        Response<List<KlineDto>> response = kline1dApi.saveAll(List.of(k1, k2)).execute();

        assertTrue(response.isSuccessful());
        assertEquals(2, response.body().size());
        System.out.println("✅ Lista masiva persistida exitosamente.");
    }

    @Test
    @Order(11)
    @DisplayName("Prueba End-to-End: Eliminar registros de prueba")
    void testDeleteEndpoints() throws Exception {
        for (Long id : ids) {
            Response<Void> response = kline15mApi.deleteById(id).execute();
            assertEquals(200, response.code());

        }

        for (Long id : ids) {
            Response<Void> response = kline5mApi.deleteById(id).execute();
            assertEquals(200, response.code());

        }

        for (Long id : ids) {
            Response<Void> response = kline1hApi.deleteById(id).execute();
            assertEquals(200, response.code());

        }

        for (Long id : ids) {
            Response<Void> response = kline4hApi.deleteById(id).execute();
            assertEquals(200, response.code());
        }

        for (Long id : ids) {
            Response<Void> response = kline1dApi.deleteById(id).execute();
            assertEquals(200, response.code());
        }


        System.out.println("✅ Registros de prueba eliminados de la base de datos.");
    }

    private KlineDto createSampleKline1d(Long openTime, String symbol) {
        KlineDto k = new KlineDto();
        k.setOpenTime(openTime);
        k.setCloseTime(openTime + 1);
        k.setSymbol(symbol);
        k.setOpenPrice(50000.0);
        k.setClosePrice(51000.0);
        k.setMaxPrice(52000.0);
        k.setMinPrice(49000.0);
        return k;
    }
}
