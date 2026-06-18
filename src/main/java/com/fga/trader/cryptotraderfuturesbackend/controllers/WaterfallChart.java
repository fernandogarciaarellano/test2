package com.fga.trader.cryptotraderfuturesbackend.controllers;

import com.fga.tradermodel.dto.KlineDto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class WaterfallChart {

    private static final int GRAPH_WIDTH = 100;

    private static final String RESET = "\u001B[0m";
    private static final String RED_BG = "\u001B[41m";
    private static final String GREEN_BG = "\u001B[42m";
    private static final String RED_TXT = "\u001B[31m";
    private static final String GREEN_TXT = "\u001B[32m";

    private static final String WICK_CHAR = "-";
    private static final String BODY_CHAR = " ";

    public static void printChart(final List<KlineDto> klines) {
        if (klines == null || klines.isEmpty()) {
            System.out.println("Sin datos.");
            return;
        }

        double minPrice = klines.stream().map(KlineDto::getMinPrice).min(Comparator.naturalOrder()).orElse(0.0);
        double maxPrice = klines.stream().map(KlineDto::getMaxPrice).max(Comparator.naturalOrder()).orElse(10.0);

        double range = maxPrice - minPrice;
        if (range == 0) range = 1.0;

        double scale = GRAPH_WIDTH / range;

        System.out.println("\nGRAFICO DE CASCADA: " + klines.getFirst().getSymbol());
        printPriceRuler(minPrice, maxPrice);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

        for (KlineDto k : klines) {
            printKlineRow(k, minPrice, scale, fmt);
        }
        System.out.println(RESET);
        Trend trend = TrendAnalyzer.detectTrend(klines.subList(0, klines.size() - 1));
        System.out.println(trend);
    }

    private static void printPriceRuler(double min, double max) {
        double mid = (min + max) / 2;
        double q1 = (min + mid) / 2;
        double q3 = (mid + max) / 2;

        System.out.printf("       Hora | %-24s %-24s %-24s %-24s %s%n",
                fmtPrice(min), fmtPrice(q1), fmtPrice(mid), fmtPrice(q3), fmtPrice(max));
        System.out.println("------------+" + "-".repeat(GRAPH_WIDTH + 5));
    }

    private static void printKlineRow(KlineDto k, double min, double scale, DateTimeFormatter fmt) {
        double open  = k.getOpenPrice();
        double close = k.getClosePrice();
        double high  = k.getMaxPrice();
        double low   = k.getMinPrice();

        boolean isBullish = close >= open;
        String bgInfo  = isBullish ? GREEN_BG : RED_BG;
        String txtInfo = isBullish ? GREEN_TXT : RED_TXT;

        int idxLow   = scale(low,   min, scale);
        int idxHigh  = scale(high,  min, scale);
        int idxOpen  = scale(open,  min, scale);
        int idxClose = scale(close, min, scale);

        int bodyStart = Math.min(idxOpen, idxClose);
        int bodyEnd   = Math.max(idxOpen, idxClose);

        StringBuilder row = new StringBuilder();
        row.append(" ".repeat(Math.max(0, idxLow)));
        row.append(WICK_CHAR.repeat(Math.max(0, bodyStart - idxLow)));
        row.append(bgInfo);
        int bodySize = bodyEnd - bodyStart;
        if (bodySize == 0) {
            row.append("|");
        } else {
            row.append(" ".repeat(bodySize));
        }
        row.append(RESET);
        row.append(WICK_CHAR.repeat(Math.max(0, idxHigh - bodyEnd)));

        System.out.printf(" %s | %s  " + txtInfo + "open: %.10f, high: %.10f, close: %.10f, low: %.10f" + RESET + "%n",
                fmt.format(Instant.ofEpochMilli(k.getCloseTime())),
                row, open, high, close, low);
    }

    private static int scale(double val, double min, double scale) {
        int idx = (int) Math.round((val - min) * scale);
        return Math.min(Math.max(idx, 0), GRAPH_WIDTH);
    }

    private static String fmtPrice(double val) {
        return String.format("%.2f", val);
    }

//    public static void main(String[] args) {
//        LocalDateTime now = LocalDateTime.now();
//        int minutosSobrantes = now.getMinute() % 15;
//        LocalDateTime ldtEnd = now.minusMinutes(minutosSobrantes)
//                .withSecond(0)
//                .withNano(0)
//                .minusSeconds(1);
//        LocalDateTime ldtStart = ldtEnd.minusMinutes(4485);
//        List<KlineDto> klines = KlinesReader.getKlines("ETHUSDT", Interval.INTERVAL_15m, ldtStart, ldtEnd);
//        klines.forEach(System.out::println);
//        System.out.println(klines.size());
//    }
}