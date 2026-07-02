package com.bytd.forward.web;

import com.bytd.forward.service.DebugService;
import com.bytd.forward.web.dto.DebugCaptureRequest;
import com.bytd.forward.web.dto.DebugRunRequest;
import com.bytd.forward.web.dto.DebugRunResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final DebugService debugService;

    public DebugController(DebugService debugService) {
        this.debugService = debugService;
    }

    /** 运行调试：编译并在沙箱执行模拟输入，返回 output/log/错误/耗时。 */
    @PostMapping("/run")
    public DebugRunResult run(@RequestBody DebugRunRequest req) {
        return debugService.run(req);
    }

    /** 从真实数据源抓取实时样本供调试。 */
    @PostMapping("/capture")
    public Map<String, Object> capture(@RequestBody DebugCaptureRequest req) throws Exception {
        List<String> samples = debugService.capture(req.sourceId, req.max, req.timeoutMs);
        return Map.of("samples", samples, "count", samples.size());
    }
}
