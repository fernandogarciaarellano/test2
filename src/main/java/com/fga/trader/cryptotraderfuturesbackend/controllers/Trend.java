package com.fga.trader.cryptotraderfuturesbackend.controllers;

public enum Trend {
    /**
     * price increased at least 1%.
     */
    BULLISH,

    /**
     * price decreased at least 1%
     */
    BEARISH,

    /**
     * price is in range 1% (up or down).
     */
    NEUTRAL;
    
}