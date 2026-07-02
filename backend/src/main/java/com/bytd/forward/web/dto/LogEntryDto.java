package com.bytd.forward.web.dto;

import java.time.Instant;

public record LogEntryDto(long id, long protocolId, String level, String message, Instant logTime) {
}
