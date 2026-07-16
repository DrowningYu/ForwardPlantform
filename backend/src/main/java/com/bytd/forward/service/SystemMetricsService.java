package com.bytd.forward.service;

import com.bytd.forward.web.dto.SystemResourceDto;
import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * 读取 JVM 与操作系统资源占用（JDK {@link OperatingSystemMXBean}）。
 */
@Service
public class SystemMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricsService.class);

    public SystemResourceDto snapshot() {
        try {
            java.lang.management.OperatingSystemMXBean raw =
                    ManagementFactory.getOperatingSystemMXBean();
            if (!(raw instanceof OperatingSystemMXBean os)) {
                return SystemResourceDto.unavailable();
            }

            double systemCpu = normalizeRatio(os.getCpuLoad());
            double processCpu = normalizeRatio(os.getProcessCpuLoad());
            if (processCpu > systemCpu) {
                processCpu = systemCpu;
            }
            double otherCpu = Math.max(0, systemCpu - processCpu);
            double remainingCpu = Math.max(0, 1.0 - systemCpu);

            long totalMem = os.getTotalMemorySize();
            long freeMem = os.getFreeMemorySize();
            if (totalMem <= 0) {
                return SystemResourceDto.unavailable();
            }
            if (freeMem < 0) {
                freeMem = 0;
            }
            if (freeMem > totalMem) {
                freeMem = totalMem;
            }

            MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            long jvmUsed = memory.getHeapMemoryUsage().getUsed()
                    + memory.getNonHeapMemoryUsage().getUsed();
            long systemUsed = totalMem - freeMem;
            long otherMem = Math.max(0, systemUsed - jvmUsed);
            // JVM 统计与 OS 已用可能略有偏差，保证三项之和不超过 total
            if (freeMem + jvmUsed + otherMem > totalMem) {
                otherMem = Math.max(0, totalMem - freeMem - jvmUsed);
            }

            return new SystemResourceDto(
                    roundPercent(remainingCpu * 100),
                    roundPercent(processCpu * 100),
                    roundPercent(otherCpu * 100),
                    totalMem,
                    freeMem,
                    jvmUsed,
                    otherMem,
                    true
            );
        } catch (Exception e) {
            log.debug("读取系统资源失败: {}", e.getMessage());
            return SystemResourceDto.unavailable();
        }
    }

    private static double normalizeRatio(double value) {
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return Math.min(1.0, value);
    }

    private static double roundPercent(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
