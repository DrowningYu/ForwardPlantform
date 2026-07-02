package com.bytd.forward.web.dto;

import java.util.List;

public record DebugCaseResult(String input, List<String> outputs, List<String> logs,
                              boolean success, boolean timeout, String error, long costMs) {
}
