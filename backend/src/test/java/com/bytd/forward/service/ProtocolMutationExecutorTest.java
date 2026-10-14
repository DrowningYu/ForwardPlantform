package com.bytd.forward.service;

import com.bytd.forward.runtime.ProtocolRuntimeManager;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProtocolMutationExecutorTest {
    @Test
    void runtimeActionOnlyRunsAfterSuccessfulCommit() {
        ProtocolRuntimeManager manager = mock(ProtocolRuntimeManager.class);
        doAnswer(call -> ((Supplier<?>) call.getArgument(1)).get()).when(manager).withProtocolLock(eq(1L), any());
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Runnable reload = mock(Runnable.class);
        ProtocolMutationExecutor executor = new ProtocolMutationExecutor(manager, transactions);
        assertEquals("saved", executor.execute(1L, () -> "saved", result -> reload.run()));
        var order = inOrder(transactions, reload);
        order.verify(transactions).commit(any());
        order.verify(reload).run();

        reset(reload);
        doThrow(new IllegalStateException("database offline")).when(transactions).commit(any());
        assertThrows(IllegalStateException.class, () -> executor.execute(1L, () -> "saved", result -> reload.run()));
        verifyNoInteractions(reload);
    }
}
