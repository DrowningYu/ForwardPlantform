package com.bytd.forward.log;

import java.time.Instant;

public record ForwardRecordRow(long protocolId, String input, String output,
                               boolean success, String error, Integer costMs, Instant recordTime) {
}
