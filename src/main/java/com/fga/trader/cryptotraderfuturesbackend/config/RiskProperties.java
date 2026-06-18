package com.fga.trader.cryptotraderfuturesbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "risk")
public class RiskProperties {
    private double amountUsdt;
    private double liquidationBuffer;
    private int maxLeverage;
    private double riskRewardRatio;
    private double maintenanceMargin;
}