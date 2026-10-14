package com.bytd.forward.service;

import com.bytd.forward.runtime.ProtocolRuntimeManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 同一协议的配置事务与生效操作串行；只有提交成功才接触运行时。 */
@Component
public class ProtocolMutationExecutor {
    private final ProtocolRuntimeManager manager;
    private final TransactionTemplate transaction;
    public ProtocolMutationExecutor(ProtocolRuntimeManager manager, PlatformTransactionManager transactions) {
        this.manager = manager;
        this.transaction = new TransactionTemplate(transactions);
    }
    public <T> T execute(Long id, Supplier<T> work, Consumer<T> afterCommit) {
        return manager.withProtocolLock(id, () -> {
            T result = transaction.execute(status -> work.get());
            afterCommit.accept(result);
            return result;
        });
    }
}
