package com.fga.trader.cryptotraderfuturesbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "claude")
public class ClaudeProperties {
    private String apiKey;
    private String baseUrl;
    private String model;
    private String version;
    private int maxTokens;
    private int connectTimeoutSeconds = 15;
    private int readTimeoutSeconds = 60;
}