package com.bytd.forward.web.dto;

import java.util.List;

public record BindingWarningDto(
        String code,
        String level,
        String resourceRole,
        String resourceName,
        String message,
        List<String> relatedProtocolNames
) {
}
