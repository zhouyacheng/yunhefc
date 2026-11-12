package com.yunhefc.auth.application;

import com.yunhefc.auth.sms.SmsSendException;
import com.yunhefc.auth.sms.SmsSender;
import com.yunhefc.common.config.SmsProperties;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.Hashing;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SmsCodeService {

    private static final Set<String> PURPOSES = Set.of("REGISTER", "RESET", "BIND");
    private static final int MAX_ATTEMPTS = 5;
    private static final int DEFAULT_RETRY_AFTER_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final boolean mockEnabled;
    private final String mockCode;
    private final long ttlSeconds;
    private final SmsSender smsSender;

    public SmsCodeService(StringRedisTemplate redis, SmsProperties properties, SmsSender smsSender) {
        this.redis = redis;
        this.mockEnabled = properties.isMockEnabled();
        this.mockCode = properties.getMockCode();
        this.ttlSeconds = properties.getTtlSeconds();
        this.smsSender = smsSender;
    }

    public int send(String phone, String purpose) {
        if (!PURPOSES.contains(purpose)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不支持的验证码用途");
        }
        String code = mockEnabled ? mockCode : generateCode();
        if (!mockEnabled) {
            sendOrTranslate(phone, code);
        }
        String key = key(purpose, phone);
        redis.opsForValue().set(key, code, Duration.ofSeconds(ttlSeconds));
        redis.delete(attemptsKey(key));
        return (int) ttlSeconds;
    }

    public void verify(String phone, String purpose, String code) {
        String key = key(purpose, phone);
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            String attemptKey = attemptsKey(key);
            Long attempts = redis.opsForValue().increment(attemptKey);
            if (attempts != null && attempts == 1) {
                redis.expire(attemptKey, Duration.ofSeconds(ttlSeconds));
            }
            if (attempts != null && attempts >= MAX_ATTEMPTS) {
                redis.delete(key);
                redis.delete(attemptKey);
                throw new BusinessException(ErrorCode.RATE_LIMITED, "验证码尝试次数过多",
                        Map.of("retryAfterSeconds", ttlSeconds));
            }
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "验证码无效或已过期");
        }
        redis.delete(key);
        redis.delete(attemptsKey(key));
    }

    private void sendOrTranslate(String phone, String code) {
        try {
            smsSender.send(phone, code);
        } catch (SmsSendException ex) {
            if (ex.reason() == SmsSendException.Reason.THROTTLED) {
                int retryAfter = ex.retryAfterSeconds() == null
                        ? DEFAULT_RETRY_AFTER_SECONDS
                        : Math.max(1, ex.retryAfterSeconds());
                throw new BusinessException(ErrorCode.RATE_LIMITED, "短信发送过于频繁",
                        Map.of("retryAfterSeconds", retryAfter));
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "短信服务暂不可用");
        }
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    static String key(String purpose, String phone) {
        return "yunhefc:auth:sms:" + purpose + ":" + Hashing.sha256(phone);
    }

    private String attemptsKey(String key) {
        return key + ":attempts";
    }
}
