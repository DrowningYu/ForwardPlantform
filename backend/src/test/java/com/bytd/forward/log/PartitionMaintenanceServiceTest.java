package com.bytd.forward.log;

import com.bytd.forward.config.ForwardProperties;
import com.bytd.forward.domain.repository.ProtocolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PartitionMaintenanceServiceTest {
    @Test
    void failedCreationIsPropagatedAndRetriedRatherThanReportedReady() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PartitionMaintenanceService service = new PartitionMaintenanceService(jdbc, new ForwardProperties(), mock(ProtocolRepository.class));
        doThrow(new DataAccessResourceFailureException("offline")).doNothing().when(jdbc).execute(anyString());
        assertThrows(DataAccessResourceFailureException.class, service::ensurePartitions);
        assertDoesNotThrow(service::ensurePartitions);
        verify(jdbc, times(9)).execute(anyString()); // 一次失败 + 两张表各四个分区
        service.stop();
    }
}
