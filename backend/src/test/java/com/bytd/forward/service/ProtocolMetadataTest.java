package com.bytd.forward.service;

import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.web.dto.ProtocolRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolMetadataTest {
    @Test
    void classificationAndSortMustNotReloadEvenWhenProtocolHasTopicFilter() throws Exception {
        ProtocolEntity p = new ProtocolEntity();
        p.setSourceTopics("bridge/+");
        ProtocolRequest req = new ObjectMapper().readValue(
                "{\"name\":\"renamed\",\"groupId\":5,\"sortOrder\":-10,\"logRetentionDays\":3}", ProtocolRequest.class);
        assertFalse(ProtocolService.runtimeSettingsChanged(p, req));
        assertTrue(req.hasGroupId());
        assertEquals(5L, req.getGroupId());
        assertFalse(req.hasSourceTopics());
    }

    @Test
    void explicitNullClearsGroupAndTopicButOmissionPreservesThem() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ProtocolRequest omitted = json.readValue("{}", ProtocolRequest.class);
        ProtocolRequest clear = json.readValue("{\"groupId\":null,\"sourceTopics\":null}", ProtocolRequest.class);
        assertFalse(omitted.hasGroupId());
        assertTrue(clear.hasGroupId());
        assertNull(clear.getGroupId());
        ProtocolEntity p = new ProtocolEntity();
        p.setSourceTopics("bridge/+");
        assertTrue(ProtocolService.runtimeSettingsChanged(p, clear));
        ProtocolRequest worker = json.readValue("{\"workerThreads\":1}", ProtocolRequest.class);
        assertTrue(ProtocolService.runtimeSettingsChanged(p, worker));
    }

    @Test
    void updatingRunningProtocolMetadataDoesNotStopOrReloadIt() throws Exception {
        var protocols = org.mockito.Mockito.mock(com.bytd.forward.domain.repository.ProtocolRepository.class);
        var runtime = org.mockito.Mockito.mock(com.bytd.forward.runtime.ProtocolRuntimeManager.class);
        var groups = org.mockito.Mockito.mock(com.bytd.forward.domain.repository.ProtocolGroupRepository.class);
        var transactions = org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.mockito.Mockito.when(transactions.getTransaction(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        org.mockito.Mockito.doAnswer(call -> ((java.util.function.Supplier<?>) call.getArgument(1)).get())
                .when(runtime).withProtocolLock(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
        var p = new ProtocolEntity();
        p.setId(1L); p.setName("old"); p.setSourceTopics("bridge/+");
        org.mockito.Mockito.when(protocols.findLockedById(1L)).thenReturn(java.util.Optional.of(p));
        org.mockito.Mockito.when(protocols.findById(1L)).thenReturn(java.util.Optional.of(p));
        org.mockito.Mockito.when(protocols.save(p)).thenReturn(p);
        org.mockito.Mockito.when(runtime.isRunning(1L)).thenReturn(true);
        org.mockito.Mockito.when(groups.existsById(5L)).thenReturn(true);
        var service = new ProtocolService(protocols,
                org.mockito.Mockito.mock(com.bytd.forward.domain.repository.ScriptVersionRepository.class), runtime,
                org.mockito.Mockito.mock(com.bytd.forward.engine.ScriptEngineService.class), new com.bytd.forward.config.ForwardProperties(),
                org.mockito.Mockito.mock(ProtocolBindingWarningService.class), groups,
                new ProtocolMutationExecutor(runtime, transactions));
        ProtocolRequest req = new ObjectMapper().readValue(
                "{\"name\":\"new\",\"groupId\":5,\"sortOrder\":-10,\"logRetentionDays\":3}", ProtocolRequest.class);
        var result = service.update(1L, req);
        assertEquals("bridge/+", result.sourceTopics());
        assertEquals(-10, result.sortOrder());
        assertEquals(5L, result.groupId());
        org.mockito.Mockito.verify(runtime).updateDisplay(1L, "new", -10);
        org.mockito.Mockito.verify(runtime, org.mockito.Mockito.never()).restart(org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.verify(runtime, org.mockito.Mockito.never()).stop(org.mockito.ArgumentMatchers.anyLong());
    }
}
