package com.yunhefc.infra.storage;

/**
 * 私有 OSS 访问端口；domain 与 controller 不接触 OSS SDK。
 */
public interface OssGateway {

    void putObject(String objectKey, byte[] content, String contentType);

    /**
     * 生成读出用的 15 分钟签名 URL。
     */
    String signedUrl(String objectKey);

    boolean exists(String objectKey);

    byte[] getObject(String objectKey);
}
