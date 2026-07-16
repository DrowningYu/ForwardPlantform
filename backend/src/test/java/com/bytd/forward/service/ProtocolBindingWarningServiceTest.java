package com.bytd.forward.service;

import com.bytd.forward.domain.entity.DataSourceEntity;
import com.bytd.forward.domain.entity.OutputTargetEntity;
import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.repository.DataSourceRepository;
import com.bytd.forward.domain.repository.OutputTargetRepository;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import com.bytd.forward.web.dto.BindingCheckResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtocolBindingWarningServiceTest {

    @Mock
    private ProtocolRepository protocolRepository;
    @Mock
    private DataSourceRepository dataSourceRepository;
    @Mock
    private OutputTargetRepository outputTargetRepository;
    @Mock
    private ProtocolRuntimeManager runtimeManager;

    private ProtocolBindingWarningService service;

    @BeforeEach
    void setUp() {
        service = new ProtocolBindingWarningService(
                protocolRepository, dataSourceRepository, outputTargetRepository,
                runtimeManager);
    }

    @Test
    void noWarningWhenNoPeers() {
        when(dataSourceRepository.findById(1L)).thenReturn(Optional.of(mqttSource("src1")));
        when(protocolRepository.findBySourceIdAndIdNot(1L, 10L)).thenReturn(List.of());

        BindingCheckResultDto result = service.analyze(10L, 1L, null, StartCheckMode.CONFIG);
        assertTrue(result.blockers().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void sharedSourceIsWarnNotBlock() {
        when(dataSourceRepository.findById(1L)).thenReturn(Optional.of(mqttSource("src1")));
        when(protocolRepository.findBySourceIdAndIdNot(1L, 10L))
                .thenReturn(List.of(peer(2L, "协议B")));

        BindingCheckResultDto result = service.analyze(10L, 1L, null, StartCheckMode.CONFIG);
        assertTrue(result.blockers().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("SHARED_SOURCE", result.warnings().getFirst().code());
        assertEquals("WARN", result.warnings().getFirst().level());
    }

    @Test
    void sharedSinkIsWarn() {
        when(outputTargetRepository.findById(5L)).thenReturn(Optional.of(httpSink("http-out")));
        when(protocolRepository.findByOutputTargetIdAndIdNot(5L, 10L))
                .thenReturn(List.of(peer(4L, "协议D")));

        BindingCheckResultDto result = service.analyze(10L, null, 5L, StartCheckMode.CONFIG);
        assertTrue(result.blockers().isEmpty());
        assertEquals(1, result.warnings().size());
        assertEquals("SHARED_SINK", result.warnings().getFirst().code());
    }

    @Test
    void startModeOnlyCountsRunningPeers() {
        when(dataSourceRepository.findById(1L)).thenReturn(Optional.of(mqttSource("src1")));
        when(protocolRepository.findBySourceIdAndIdNot(1L, 10L))
                .thenReturn(List.of(peer(2L, "协议B")));
        when(runtimeManager.isRunning(2L)).thenReturn(false);

        BindingCheckResultDto result = service.analyze(10L, 1L, null, StartCheckMode.START);
        assertTrue(result.blockers().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void startModeWarnRequiresAck() {
        when(protocolRepository.findById(10L)).thenReturn(Optional.of(protocol(10L, 1L, null)));
        when(dataSourceRepository.findById(1L)).thenReturn(Optional.of(mqttSource("src1")));
        when(protocolRepository.findBySourceIdAndIdNot(1L, 10L))
                .thenReturn(List.of(peer(2L, "协议B")));
        when(runtimeManager.isRunning(2L)).thenReturn(true);

        assertThrows(ProtocolBindingWarningException.class,
                () -> service.assertCanStart(10L, false));
    }

    @Test
    void startModeWarnAcknowledgedPasses() {
        when(protocolRepository.findById(10L)).thenReturn(Optional.of(protocol(10L, 1L, null)));
        when(dataSourceRepository.findById(1L)).thenReturn(Optional.of(mqttSource("src1")));
        when(protocolRepository.findBySourceIdAndIdNot(1L, 10L))
                .thenReturn(List.of(peer(2L, "协议B")));
        when(runtimeManager.isRunning(2L)).thenReturn(true);

        service.assertCanStart(10L, true);
    }

    @Test
    void fixedClientIdNoLongerBlocks() {
        // 共享连接架构下固定 clientId 不再互斥，仅提示共享
        when(protocolRepository.findById(10L)).thenReturn(Optional.of(protocol(10L, 1L, null)));
        when(dataSourceRepository.findById(1L)).thenReturn(Optional.of(mqttSource("src1")));
        when(protocolRepository.findBySourceIdAndIdNot(1L, 10L))
                .thenReturn(List.of(peer(2L, "协议B")));
        when(runtimeManager.isRunning(2L)).thenReturn(true);

        BindingCheckResultDto result = service.analyzeForStart(10L);
        assertTrue(result.blockers().isEmpty());
        assertEquals(1, result.warnings().size());
    }

    private static DataSourceEntity mqttSource(String name) {
        DataSourceEntity ds = new DataSourceEntity();
        ds.setId(1L);
        ds.setName(name);
        ds.setType("MQTT");
        ds.setConfig("{\"url\":\"tcp://localhost:1883\",\"topics\":\"t/#\",\"clientId\":\"fixed-id\"}");
        return ds;
    }

    private static OutputTargetEntity httpSink(String name) {
        OutputTargetEntity ot = new OutputTargetEntity();
        ot.setId(5L);
        ot.setName(name);
        ot.setType("HTTP");
        ot.setConfig("{\"url\":\"http://localhost/api\",\"method\":\"POST\"}");
        return ot;
    }

    private static ProtocolEntity peer(Long id, String name) {
        ProtocolEntity p = new ProtocolEntity();
        p.setId(id);
        p.setName(name);
        return p;
    }

    private static ProtocolEntity protocol(Long id, Long sourceId, Long outputTargetId) {
        ProtocolEntity p = new ProtocolEntity();
        p.setId(id);
        p.setName("协议" + id);
        p.setSourceId(sourceId);
        p.setOutputTargetId(outputTargetId);
        return p;
    }
}
