package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.infra.lock.RedisLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

class RedisLockIT extends IntegrationTestSupport {

    private static final Duration TTL = Duration.ofSeconds(5);

    @Autowired
    RedisLock lock;

    @Test
    void sameKeyIsMutuallyExclusive() {
        String key = RedisLock.KEY_PREFIX + "activity:1:signup";
        String token = lock.tryLock(key, TTL);

        assertThat(token).isNotBlank();
        assertThat(lock.tryLock(key, TTL)).isNull();
    }

    @Test
    void differentKeysDoNotBlockEachOther() {
        assertThat(lock.tryLock(RedisLock.KEY_PREFIX + "activity:1:signup", TTL)).isNotBlank();
        assertThat(lock.tryLock(RedisLock.KEY_PREFIX + "activity:2:signup", TTL)).isNotBlank();
    }

    @Test
    void unlockWithWrongTokenDoesNotRelease() {
        String key = RedisLock.KEY_PREFIX + "activity:3:signup";
        String token = lock.tryLock(key, TTL);

        lock.unlock(key, "someone-else-token");
        assertThat(lock.tryLock(key, TTL)).isNull();

        lock.unlock(key, token);
        assertThat(lock.tryLock(key, TTL)).isNotBlank();
    }

    @Test
    void expiredTtlAllowsAnotherHolder() throws InterruptedException {
        String key = RedisLock.KEY_PREFIX + "activity:4:signup";
        assertThat(lock.tryLock(key, Duration.ofMillis(100))).isNotBlank();

        Thread.sleep(300);

        assertThat(lock.tryLock(key, TTL)).isNotBlank();
    }

    @Test
    void retryReturnsNullWhenKeyStaysLocked() {
        String key = RedisLock.KEY_PREFIX + "activity:5:signup";
        lock.tryLock(key, TTL);

        assertThat(lock.tryLockWithRetry(key, TTL)).isNull();
    }
}
