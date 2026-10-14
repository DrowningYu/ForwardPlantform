package com.bytd.forward.runtime;

import com.bytd.forward.config.ForwardProperties;
import com.bytd.forward.domain.repository.*;
import com.bytd.forward.engine.ScriptEngineService;
import com.bytd.forward.log.AsyncLogWriter;
import com.bytd.forward.runtime.shared.SharedSinkManager;
import com.bytd.forward.runtime.shared.SharedSourceManager;
import com.bytd.forward.service.ProtocolBindingWarningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeStatusOfflineTest {
    @Test
    void sortedStatusReadsNoDatabaseAndUsesIdToBreakTies() {
        ProtocolRepository protocols = mock(ProtocolRepository.class);
        DataSourceRepository sources = mock(DataSourceRepository.class);
        OutputTargetRepository targets = mock(OutputTargetRepository.class);
        ScriptVersionRepository scripts = mock(ScriptVersionRepository.class);
        var manager = new ProtocolRuntimeManager(protocols, sources, targets, scripts,
                mock(ScriptEngineService.class), mock(SharedSourceManager.class), mock(SharedSinkManager.class),
                mock(AsyncLogWriter.class), new ObjectMapper(), new ForwardProperties(), mock(ProtocolBindingWarningService.class));
        ReflectionTestUtils.setField(manager, "runtimes", Map.of(3L, runtime(3, 0), 2L, runtime(2, -10), 1L, runtime(1, 0)));
        assertEquals(java.util.List.of(2L, 1L, 3L), manager.allStatuses().stream().map(RuntimeStatus::protocolId).toList());
        verifyNoInteractions(protocols, sources, targets, scripts);
    }

    private ProtocolRuntime runtime(long id, int order) {
        ProtocolRuntime runtime = mock(ProtocolRuntime.class);
        RuntimeStatus status = mock(RuntimeStatus.class);
        when(status.protocolId()).thenReturn(id);
        when(status.sortOrder()).thenReturn(order);
        when(runtime.snapshot()).thenReturn(status);
        return runtime;
    }
}
