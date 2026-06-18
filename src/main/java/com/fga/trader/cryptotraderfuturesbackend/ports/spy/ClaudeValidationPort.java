package com.fga.trader.cryptotraderfuturesbackend.ports.spy;

import com.fga.trader.cryptotraderfuturesbackend.records.ClaudeVerdict;
import com.fga.trader.cryptotraderfuturesbackend.records.FVGCandidate;

public interface ClaudeValidationPort {
    ClaudeVerdict validateFvg(FVGCandidate candidate);
}