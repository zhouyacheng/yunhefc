package com.yunhefc.common.ratelimit;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class RedisRateLimiter {

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String key, int limit, Duration window) {
        String redisKey = "yunhefc:rate:" + key;
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redis.expire(redisKey, window);
        }
        if (count != null && count > limit) {
            Long ttl = redis.getExpire(redisKey);
            long retryAfter = ttl == null || ttl < 0 ? window.toSeconds() : ttl;
            throw new BusinessException(ErrorCode.RATE_LIMITED, "请求过于频繁",
                    Map.of("retryAfterSeconds", Math.max(1, retryAfter)));
        }
    }
}
