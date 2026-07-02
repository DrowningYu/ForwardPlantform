package com.bytd.forward.web.dto;

import java.time.Instant;

public record ForwardRecordDto(long id, long protocolId, String input, String output,
                               boolean success, String error, Integer costMs, Instant recordTime) {
}
