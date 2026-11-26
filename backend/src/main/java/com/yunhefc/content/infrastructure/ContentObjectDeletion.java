package com.yunhefc.content.infrastructure;

import com.yunhefc.infra.storage.ObjectRemoval;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

/**
 * 内容对象删除：记录伪删除成功后 best-effort 删除 OSS 对象。
 * 存在活动事务时推迟到提交后执行，避免回滚后对象已被删除；删除与降级由 ObjectRemoval 负责。
 */
@Component
@RequiredArgsConstructor
public class ContentObjectDeletion {

    private final ObjectRemoval objectRemoval;

    public void deleteAll(Collection<String> objectKeys) {
        if (objectKeys == null) {
            return;
        }
        List<String> keys = objectKeys.stream()
                .filter(objectKey -> objectKey != null && !objectKey.isBlank())
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    objectRemoval.removeAll(keys);
                }
            });
        } else {
            objectRemoval.removeAll(keys);
        }
    }
}
