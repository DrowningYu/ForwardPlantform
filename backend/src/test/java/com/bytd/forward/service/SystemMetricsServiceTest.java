package com.bytd.forward.service;

import com.bytd.forward.web.dto.SystemResourceDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMetricsServiceTest {

  private final SystemMetricsService service = new SystemMetricsService();

  @Test
  void snapshotReturnsValidPercentsOrUnavailable() {
    SystemResourceDto dto = service.snapshot();
    if (dto.available()) {
      double cpuSum = dto.cpuRemainingPercent() + dto.cpuProcessPercent() + dto.cpuOtherPercent();
      assertEquals(100.0, cpuSum, 0.2);
      assertTrue(dto.memTotalBytes() > 0);
      long memSum = dto.memFreeBytes() + dto.memProcessBytes() + dto.memOtherBytes();
      assertTrue(memSum <= dto.memTotalBytes());
      assertTrue(dto.memProcessBytes() >= 0);
    }
  }
}
