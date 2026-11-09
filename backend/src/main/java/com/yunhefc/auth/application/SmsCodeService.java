package com.yunhefc.auth.application;

import com.yunhefc.common.config.SmsProperties;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Service
public class SmsCodeService {

    private static final Set<String> PURPOSES = Set.of("REGISTER", "RESET", "BIND");
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redis;
    private final boolean mockEnabled;
    private final String mockCode;
    private final long ttlSeconds;

    public SmsCodeService(StringRedisTemplate redis, SmsProperties properties) {
        this.redis = redis;
        this.mockEnabled = properties.isMockEnabled();
        this.mockCode = properties.getMockCode();
        this.ttlSeconds = properties.getTtlSeconds();
    }

    public int send(String phone, String purpose) {
        if (!PURPOSES.contains(purpose)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不支持的验证码用途");
        }
        requireMockEnabled();
        redis.opsForValue().set(key(phone, purpose), mockCode, Duration.ofSeconds(ttlSeconds));
        return (int) ttlSeconds;
    }

    public void verify(String phone, String purpose, String code) {
        requireMockEnabled();
        String key = key(phone, purpose);
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            String attemptsKey = key + ":attempts";
            Long attempts = redis.opsForValue().increment(attemptsKey);
            if (attempts != null && attempts == 1) {
                redis.expire(attemptsKey, Duration.ofSeconds(ttlSeconds));
            }
            if (attempts != null && attempts >= MAX_ATTEMPTS) {
                redis.delete(key);
                redis.delete(attemptsKey);
                throw new BusinessException(ErrorCode.RATE_LIMITED, "验证码尝试次数过多",
                        Map.of("retryAfterSeconds", ttlSeconds));
            }
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "验证码无效或已过期");
        }
        redis.delete(key);
        redis.delete(key + ":attempts");
    }

    private void requireMockEnabled() {
        if (!mockEnabled) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "短信服务暂不可用");
        }
    }

    private String key(String phone, String purpose) {
        return "yunhefc:auth:sms:" + purpose + ":" + phone;
    }
}
