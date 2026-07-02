package com.bytd.forward.web;

import com.bytd.forward.log.PartitionMaintenanceService;
import com.bytd.forward.service.LogQueryService;
import com.bytd.forward.web.dto.ForwardRecordDto;
import com.bytd.forward.web.dto.LogEntryDto;
import com.bytd.forward.web.dto.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogQueryService logQueryService;
    private final PartitionMaintenanceService partitionMaintenance;

    public LogController(LogQueryService logQueryService, PartitionMaintenanceService partitionMaintenance) {
        this.logQueryService = logQueryService;
        this.partitionMaintenance = partitionMaintenance;
    }

    @GetMapping
    public PageResult<LogEntryDto> logs(@RequestParam(required = false) Long protocolId,
                                        @RequestParam(required = false) String level,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Long fromMs,
                                        @RequestParam(required = false) Long toMs,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return logQueryService.queryLogs(protocolId, level, keyword, fromMs, toMs, page, Math.min(size, 500));
    }

    @GetMapping("/records")
    public PageResult<ForwardRecordDto> records(@RequestParam(required = false) Long protocolId,
                                                @RequestParam(required = false) Boolean success,
                                                @RequestParam(required = false) Long fromMs,
                                                @RequestParam(required = false) Long toMs,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return logQueryService.queryRecords(protocolId, success, fromMs, toMs, page, Math.min(size, 500));
    }

    /** 手动触发过期分区清理。 */
    @PostMapping("/purge")
    public Map<String, Object> purge() {
        partitionMaintenance.dropExpiredPartitions();
        return Map.of("ok", true);
    }
}
