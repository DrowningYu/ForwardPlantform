package com.bytd.forward.service;

import com.bytd.forward.web.dto.BindingCheckResultDto;

public class ProtocolBindingWarningException extends RuntimeException {

    private final BindingCheckResultDto result;

    public ProtocolBindingWarningException(BindingCheckResultDto result) {
        super("协议绑定存在需确认的警告项");
        this.result = result;
    }

    public BindingCheckResultDto getResult() {
        return result;
    }
}
