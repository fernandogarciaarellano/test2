package com.fga.trader.cryptotraderfuturesbackend.records;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClaudeVerdict(
        @JsonProperty("isValid") boolean isValid,
        @JsonProperty("classification") String classification,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("reasoning") String reasoning
) {}