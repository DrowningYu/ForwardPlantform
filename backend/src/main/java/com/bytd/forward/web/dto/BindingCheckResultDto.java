package com.bytd.forward.web.dto;

import java.util.List;

public record BindingCheckResultDto(
        List<BindingWarningDto> blockers,
        List<BindingWarningDto> warnings
) {
    public static BindingCheckResultDto empty() {
        return new BindingCheckResultDto(List.of(), List.of());
    }
}
