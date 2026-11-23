package com.yunhefc.infra.lock;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis 分布式锁：{@code SET NX PX} 获取，Lua 比较 token 后删除。
 * <p>
 * key 由调用方拼装，统一前缀 {@link #KEY_PREFIX}，不加环境前缀（P-46），
 * 例如 {@code yunhefc:lock:activity:{activityId}:signup}。
 * 不提供可重入；释放时机由调用方保证（活动报名在事务提交/回滚后释放，避免锁先于提交）。
 */
@Component
@RequiredArgsConstructor
public class RedisLock {

    public static final String KEY_PREFIX = "yunhefc:lock:";

    private static final Logger log = LoggerFactory.getLogger(RedisLock.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_INTERVAL_MILLIS = 50L;
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    /** 返回本次持有的 token；key 已被占用返回 null。 */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (DataAccessException ex) {
            log.warn("redis lock acquire failed, key={}", key, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 获取失败每 50ms 重试一次，至多 3 次；仍失败返回 null，由业务层映射错误码
     * （活动报名 → 40919），infra 不耦合业务错误。
     */
    public String tryLockWithRetry(String key, Duration ttl) {
        String token = tryLock(key, ttl);
        for (int retry = 0; token == null && retry < MAX_RETRIES; retry++) {
            sleep();
            token = tryLock(key, ttl);
        }
        return token;
    }

    /** 仅当 token 仍持有锁时删除，避免误删他人重新获取的锁。 */
    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        try {
            redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (DataAccessException ex) {
            log.warn("redis lock release failed, key={}", key, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("redis lock retry interrupted", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
