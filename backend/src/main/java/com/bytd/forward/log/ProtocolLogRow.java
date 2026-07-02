package com.bytd.forward.log;

import java.time.Instant;

public record ProtocolLogRow(long protocolId, String level, String message, Instant logTime) {
}
