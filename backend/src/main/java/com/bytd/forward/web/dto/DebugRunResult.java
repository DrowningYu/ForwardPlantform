package com.bytd.forward.web.dto;

import java.util.List;

public record DebugRunResult(boolean compileOk, String compileError, List<DebugCaseResult> cases) {
}
