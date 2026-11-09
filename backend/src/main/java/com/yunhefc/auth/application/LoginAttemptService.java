package com.yunhefc.auth.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public LoginAttemptService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String phone) {
        String value = redis.opsForValue().get(key(phone));
        if (value == null) {
            return;
        }
        long failures = Long.parseLong(value);
        if (failures >= MAX_FAILURES) {
            Long ttl = redis.getExpire(key(phone));
            long retryAfter = ttl == null || ttl < 0 ? LOCK_TTL.toSeconds() : ttl;
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "账号已锁定，请稍后再试",
                    Map.of("retryAfterSeconds", Math.max(1, retryAfter)));
        }
    }

    public void recordFailure(String phone) {
        String key = key(phone);
        Long failures = redis.opsForValue().increment(key);
        if (failures != null && failures == 1) {
            redis.expire(key, LOCK_TTL);
        }
        if (failures != null && failures >= MAX_FAILURES) {
            redis.expire(key, LOCK_TTL);
        }
    }

    public void clear(String phone) {
        redis.delete(key(phone));
    }

    private String key(String phone) {
        return "yunhefc:auth:login-fail:" + phone;
    }
}
