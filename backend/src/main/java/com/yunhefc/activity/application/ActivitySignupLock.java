package com.yunhefc.activity.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.infra.lock.RedisLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

/**
 * 活动报名维度锁：报名与「调低人数上限」共用，保证计数与写入不被交错。
 * 锁在事务提交/回滚后释放，禁止在事务内 finally 释放。
 */
@Component
@RequiredArgsConstructor
class ActivitySignupLock {

    private static final Duration TTL = Duration.ofSeconds(5);

    private final RedisLock redisLock;

    void acquire(Long activityId) {
        String key = RedisLock.KEY_PREFIX + "activity:" + activityId + ":signup";
        String token = redisLock.tryLockWithRetry(key, TTL);
        if (token == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_SIGNUP_BUSY);
        }
        releaseAfterTransaction(key, token);
    }

    private void releaseAfterTransaction(String key, String token) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            redisLock.unlock(key, token);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                redisLock.unlock(key, token);
            }
        });
    }
}
