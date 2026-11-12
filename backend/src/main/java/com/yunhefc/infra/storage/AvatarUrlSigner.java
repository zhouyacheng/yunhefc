package com.yunhefc.infra.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 头像读响应组装时现签 OSS URL；签名结果只用于响应，不落库、不写入审计快照。
 */
@Component
@RequiredArgsConstructor
public class AvatarUrlSigner {

    private final OssGateway ossGateway;

    public String sign(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return ossGateway.signedUrl(objectKey);
    }
}
