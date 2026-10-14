package com.bytd.forward.service;

import com.bytd.forward.domain.entity.ProtocolEntity;
import com.bytd.forward.domain.entity.ScriptVersionEntity;
import com.bytd.forward.domain.repository.ProtocolRepository;
import com.bytd.forward.domain.repository.ScriptVersionRepository;
import com.bytd.forward.engine.CompileResult;
import com.bytd.forward.engine.ScriptEngineService;
import com.bytd.forward.runtime.ProtocolRuntimeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ScriptServiceTest {
    private final ScriptVersionRepository versions = mock(ScriptVersionRepository.class);
    private final ProtocolRepository protocols = mock(ProtocolRepository.class);
    private final ScriptEngineService engine = mock(ScriptEngineService.class);
    private final ProtocolRuntimeManager runtime = mock(ProtocolRuntimeManager.class);
    private ScriptService service;
    private ProtocolEntity protocol;
    private ScriptVersionEntity current;
    private ScriptVersionEntity historical;

    @BeforeEach
    void setup() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doAnswer(call -> ((Supplier<?>) call.getArgument(1)).get()).when(runtime).withProtocolLock(eq(1L), any());
        service = new ScriptService(versions, protocols, engine, runtime, new ProtocolMutationExecutor(runtime, transactions));
        protocol = new ProtocolEntity();
        protocol.setId(1L); protocol.setCurrentVersionId(10L); protocol.setNextScriptVersion(7);
        current = version(10L, 1L, 5);
        historical = version(9L, 1L, 4);
        when(protocols.findById(1L)).thenReturn(Optional.of(protocol));
        when(protocols.findLockedById(1L)).thenReturn(Optional.of(protocol));
        when(versions.findById(10L)).thenReturn(Optional.of(current));
        when(versions.findById(9L)).thenReturn(Optional.of(historical));
        when(versions.saveAndFlush(any())).thenAnswer(call -> {
            ScriptVersionEntity v = call.getArgument(0);
            if (v.getId() == null) {
                v.setId(11L);
                when(versions.findById(11L)).thenReturn(Optional.of(v));
            } else { v.setRevision(v.getRevision() + 1); }
            return v;
        });
        when(engine.compile(anyString())).thenReturn(CompileResult.ok(null));
    }

    @Test
    void currentVersionCannotBeDeleted() {
        assertThrows(IllegalStateException.class, () -> service.delete(1L, 10L));
        verify(versions, never()).delete(any());
        service.delete(1L, 9L);
        verify(versions).delete(historical);
        verify(runtime, never()).reloadIfRunning(anyLong());
    }

    @Test
    void wrongProtocolCannotReadReviseActivateOrDeleteVersion() {
        historical.setProtocolId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.getVersion(1L, 9L));
        assertThrows(IllegalArgumentException.class, () -> service.revise(1L, 9L, "new", "", 0L));
        assertThrows(IllegalArgumentException.class, () -> service.switchVersion(1L, 9L));
        assertThrows(IllegalArgumentException.class, () -> service.delete(1L, 9L));
        verify(versions, never()).delete(any());
        verify(runtime, never()).reloadIfRunning(anyLong());
    }

    @Test
    void failedCompilationKeepsOriginalCodeAndRuntime() {
        when(engine.compile("bad")).thenReturn(CompileResult.fail("syntax error"));
        assertThrows(IllegalArgumentException.class, () -> service.revise(1L, 10L, "bad", "", 0L));
        assertEquals("old", current.getCode());
        verify(versions, never()).saveAndFlush(any());
        verify(runtime, never()).reloadIfRunning(anyLong());
    }

    @Test
    void staleRevisionIsRejectedBeforeCompilation() {
        current.setRevision(2L);
        assertThrows(IllegalStateException.class, () -> service.revise(1L, 10L, "new", "", 1L));
        verifyNoInteractions(engine);
    }

    @Test
    void remarkOnlyDoesNotReloadButCurrentCodeChangeDoes() {
        service.revise(1L, 10L, "old", "new remark", 0L);
        verify(runtime, never()).reloadIfRunning(anyLong());
        verifyNoInteractions(engine);
        service.revise(1L, 10L, "new code", "new remark", 1L);
        verify(runtime).reloadIfRunning(1L);
    }

    @Test
    void revisingHistoricalVersionDoesNotActivateIt() {
        service.revise(1L, 9L, "new code", "remark", 0L);
        assertEquals(10L, protocol.getCurrentVersionId());
        verify(runtime, never()).reloadIfRunning(anyLong());
    }

    @Test
    void deletingHighestVersionDoesNotReuseVersionNumber() {
        ScriptVersionEntity highest = version(12L, 1L, 6);
        when(versions.findById(12L)).thenReturn(Optional.of(highest));
        service.delete(1L, 12L);
        var saved = service.saveNewVersion(1L, "new code", "");
        assertEquals(7, saved.version());
        assertEquals(8, protocol.getNextScriptVersion());
        assertTrue(saved.current());
    }

    @Test
    void failedNewVersionIsSavedWithoutChangingCurrent() {
        when(engine.compile("bad")).thenReturn(CompileResult.fail("syntax error"));
        var saved = service.saveNewVersion(1L, "bad", "");
        assertEquals("ERROR", saved.compileStatus());
        assertFalse(saved.current());
        assertEquals(10L, protocol.getCurrentVersionId());
        verify(runtime, never()).reloadIfRunning(anyLong());
    }

    @Test
    void stoppedProtocolHasCurrentMarkerButNoRunningMarker() {
        when(versions.findByProtocolIdOrderByVersionDesc(1L)).thenReturn(List.of(current, historical));
        var list = service.listVersions(1L);
        assertTrue(list.getFirst().current());
        assertFalse(list.getFirst().running());
        assertFalse(list.getLast().current());
    }

    @Test
    void runningMarkerMustMatchLoadedRevisionRatherThanOnlyVersionId() {
        var status = mock(com.bytd.forward.runtime.RuntimeStatus.class);
        when(status.status()).thenReturn("RUNNING");
        when(status.scriptVersionId()).thenReturn(10L);
        when(status.scriptRevision()).thenReturn(0L);
        when(runtime.status(1L)).thenReturn(Optional.of(status));
        assertTrue(service.getVersion(1L, 10L).running());
        current.setRevision(1L);
        assertFalse(service.getVersion(1L, 10L).running());
        assertTrue(service.getVersion(1L, 10L).current());
    }

    @Test
    void newVersionCounterToleratesVersionsAddedByOlderProgram() {
        when(versions.findTopByProtocolIdOrderByVersionDesc(1L)).thenReturn(Optional.of(version(99L, 1L, 20)));
        assertEquals(21, service.saveNewVersion(1L, "new code", "").version());
        assertEquals(22, protocol.getNextScriptVersion());
    }

    private ScriptVersionEntity version(long id, long pid, int number) {
        ScriptVersionEntity v = new ScriptVersionEntity();
        v.setId(id); v.setProtocolId(pid); v.setVersion(number); v.setCode("old"); v.setCompileStatus("OK");
        return v;
    }
}
