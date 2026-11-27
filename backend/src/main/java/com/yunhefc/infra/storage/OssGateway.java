package com.yunhefc.infra.storage;

/**
 * 私有 OSS 访问端口；domain 与 controller 不接触 OSS SDK。
 */
public interface OssGateway {

    void putObject(String objectKey, byte[] content, String contentType);

    /**
     * 生成读出用的 15 分钟签名 URL。
     */
    default String signedUrl(String objectKey) {
        return signedUrl(objectKey, null);
    }

    /**
     * 生成带图片处理参数的 15 分钟签名 URL；{@code process} 为空时等同 {@link #signedUrl(String)}。
     */
    String signedUrl(String objectKey, String process);

    boolean exists(String objectKey);

    byte[] getObject(String objectKey);

    void deleteObject(String objectKey);
}
