package com.yunhefc.auth.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
public class SmsCodeService {

    private static final Set<String> PURPOSES = Set.of("REGISTER", "RESET", "BIND");

    private final StringRedisTemplate redis;
    private final String mockCode;
    private final long ttlSeconds;

    public SmsCodeService(
            StringRedisTemplate redis,
            @Value("${yunhefc.sms.mock-code:123456}") String mockCode,
            @Value("${yunhefc.sms.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.mockCode = mockCode;
        this.ttlSeconds = ttlSeconds;
    }

    public int send(String phone, String purpose) {
        if (!PURPOSES.contains(purpose)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不支持的验证码用途");
        }
        redis.opsForValue().set(key(phone, purpose), mockCode, Duration.ofSeconds(ttlSeconds));
        return (int) ttlSeconds;
    }

    public void verify(String phone, String purpose, String code) {
        String key = key(phone, purpose);
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "验证码无效或已过期");
        }
        redis.delete(key);
    }

    private String key(String phone, String purpose) {
        return "yunhefc:auth:sms:" + purpose + ":" + phone;
    }
}
