package com.bytd.forward.web.dto;

import java.time.Instant;

public record ScriptVersionDto(Long id, Long protocolId, int version, String language,
                               String compileStatus, String compileError, String remark,
                               String code, Instant createdAt) {
}
