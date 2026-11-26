package com.yunhefc.content.infrastructure;

import com.yunhefc.infra.storage.OssGateway;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;

/**
 * 内容对象删除：记录伪删除成功后 best-effort 删除 OSS 对象。
 * 存在活动事务时推迟到提交后执行，避免回滚后对象已被删除；失败只记 WARN，不影响业务结果。
 */
@Component
@RequiredArgsConstructor
public class ContentObjectDeletion {

    private static final Logger log = LoggerFactory.getLogger(ContentObjectDeletion.class);

    private final OssGateway ossGateway;

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
                    deleteQuietly(keys);
                }
            });
        } else {
            deleteQuietly(keys);
        }
    }

    private void deleteQuietly(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                ossGateway.deleteObject(objectKey);
            } catch (RuntimeException ex) {
                log.warn("content object delete failed, objectKey={}", objectKey, ex);
            }
        }
    }
}
