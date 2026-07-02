package com.bytd.forward.service;

import com.bytd.forward.web.dto.BindingCheckResultDto;

public class ProtocolBindingBlockedException extends RuntimeException {

    private final BindingCheckResultDto result;

    public ProtocolBindingBlockedException(BindingCheckResultDto result) {
        super("协议绑定存在阻断项，无法启动");
        this.result = result;
    }

    public BindingCheckResultDto getResult() {
        return result;
    }
}
