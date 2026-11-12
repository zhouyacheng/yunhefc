package com.yunhefc.infra.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * dev/test 的内存实现；对象只在本进程内可见，进程重启即清空。
 */
public class InMemoryOssGateway implements OssGateway {

    private record StoredObject(byte[] content, String contentType) {
    }

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

    @Override
    public void putObject(String objectKey, byte[] content, String contentType) {
        objects.put(objectKey, new StoredObject(content.clone(), contentType));
    }

    @Override
    public String signedUrl(String objectKey) {
        return "mock://oss/" + objectKey;
    }

    @Override
    public boolean exists(String objectKey) {
        return objects.containsKey(objectKey);
    }

    @Override
    public byte[] getObject(String objectKey) {
        StoredObject object = objects.get(objectKey);
        if (object == null) {
            throw new IllegalArgumentException("object not found: " + objectKey);
        }
        return object.content().clone();
    }

    public String contentType(String objectKey) {
        StoredObject object = objects.get(objectKey);
        return object == null ? null : object.contentType();
    }

    public void shutdown() {
        // 内存实现无外部资源。
    }
}
