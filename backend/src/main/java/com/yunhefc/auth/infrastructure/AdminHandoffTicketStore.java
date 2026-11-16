package com.yunhefc.auth.infrastructure;

import com.yunhefc.common.security.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/** C→B 一次性交接票据；Redis 只存票据哈希，原始票据仅出现在签发响应与跳转 URL。 */
@Component
@RequiredArgsConstructor
public class AdminHandoffTicketStore {

    private static final String KEY_PREFIX = "yunhefc:auth:admin-handoff:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final StringRedisTemplate redis;

    public String save(Long userId, Duration ttl) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = ENCODER.encodeToString(bytes);
        redis.opsForValue().set(key(ticket), String.valueOf(userId), ttl);
        return ticket;
    }

    /** 单次消费；无效、已消费或已过期均返回 null。 */
    public Long consume(String ticket) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 128) {
            return null;
        }
        String value = redis.opsForValue().getAndDelete(key(ticket));
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String key(String ticket) {
        return KEY_PREFIX + Hashing.sha256(ticket);
    }
}
