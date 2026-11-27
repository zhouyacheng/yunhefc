package com.yunhefc.infra.storage;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * OSS 对象的 best-effort 删除：失败只记 WARN，不影响业务结果；事务时机由调用方决定。
 */
@Component
@RequiredArgsConstructor
public class ObjectRemoval {

    private static final Logger log = LoggerFactory.getLogger(ObjectRemoval.class);

    private final OssGateway ossGateway;

    public void remove(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            ossGateway.deleteObject(objectKey);
        } catch (RuntimeException ex) {
            log.warn("object delete failed, objectKey={}", objectKey, ex);
        }
    }

    public void removeAll(Collection<String> objectKeys) {
        if (objectKeys == null) {
            return;
        }
        objectKeys.forEach(this::remove);
    }
}
