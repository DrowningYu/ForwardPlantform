package com.bytd.forward.web;

import com.bytd.forward.log.AsyncLogWriter;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.runtime.RuntimeStatus;
import com.bytd.forward.service.SystemMetricsService;
import com.bytd.forward.web.dto.OverviewDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final ProtocolRuntimeManager manager;
    private final AsyncLogWriter logWriter;
    private final SystemMetricsService systemMetrics;

    public StatusController(ProtocolRuntimeManager manager,
                            AsyncLogWriter logWriter,
                            SystemMetricsService systemMetrics) {
        this.manager = manager;
        this.logWriter = logWriter;
        this.systemMetrics = systemMetrics;
    }

    @GetMapping
    public List<RuntimeStatus> all() {
        return manager.allStatuses();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RuntimeStatus> one(@PathVariable Long id) {
        return manager.status(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/overview")
    public OverviewDto overview() {
        List<RuntimeStatus> all = manager.allStatuses();
        long in = 0, out = 0, scriptErr = 0, timeout = 0, sinkErr = 0;
        int running = 0;
        for (RuntimeStatus s : all) {
            if ("RUNNING".equals(s.status())) {
                running++;
            }
            in += s.in();
            out += s.out();
            scriptErr += s.scriptError();
            timeout += s.timeout();
            sinkErr += s.sinkError();
        }
        return new OverviewDto(running, in, out, scriptErr, timeout, sinkErr,
                logWriter.getLogQueueSize(), logWriter.getRecordQueueSize(),
                logWriter.getDroppedLogs(), logWriter.getDroppedRecords(),
                systemMetrics.snapshot());
    }
}
