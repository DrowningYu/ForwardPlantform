package com.bytd.forward.web.dto;

/**
 * 主机与 JVM 资源快照，供监控大盘圆环图展示。
 * CPU 三项百分比之和为 100；内存三项字节之和为 totalBytes。
 */
public record SystemResourceDto(
        double cpuRemainingPercent,
        double cpuProcessPercent,
        double cpuOtherPercent,
        long memTotalBytes,
        long memFreeBytes,
        long memProcessBytes,
        long memOtherBytes,
        boolean available
) {
    public static SystemResourceDto unavailable() {
        return new SystemResourceDto(0, 0, 0, 0, 0, 0, 0, false);
    }
}
