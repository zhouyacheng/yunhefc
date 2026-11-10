package com.yunhefc.contracts.security;

import java.time.Instant;
import java.util.Set;

/**
 * 跨模块读取的账号快照；手机号只暴露脱敏值。
 */
public record UserAccountSnapshot(
        Long userId,
        String phoneMasked,
        Integer status,
        String statusLabel,
        Set<String> roles,
        Instant createdAt) {
}
