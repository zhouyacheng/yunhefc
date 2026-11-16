package com.yunhefc.match.infrastructure;

import com.yunhefc.match.domain.MatchStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 赛季场次编号的 Redis 列表：key 为 {@code yunhefc:season:<赛季>}，元素为该赛季在场比赛的场次号。
 * MySQL 仍是唯一事实源，Redis 只用于分配与快速读取；Redis 异常时由调用方回退数据库。
 */
@Component
@RequiredArgsConstructor
public class SeasonMatchNoStore {

    private static final Logger log = LoggerFactory.getLogger(SeasonMatchNoStore.class);
    private static final String KEY_PREFIX = "yunhefc:season:";
    // key 缺失时按 DB 活跃场次重建；EXISTS 与 RPUSH 在同一脚本内保证只种子一次。
    private static final RedisScript<Long> SEED_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            for i = 1, #ARGV do redis.call('RPUSH', KEYS[1], ARGV[i]) end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final TeamMatchMapper teamMatchMapper;

    /**
     * 取本赛季下一场次号：Redis 列表最大数字 + 1；候选被已取消/伪删除记录占用时回退 DB 最大值 + 1。
     * Redis 异常时同样回退 DB，保证新建比赛可用。
     */
    public int next(Long teamId, String season) {
        try {
            ensureSeeded(teamId, season);
            int candidate = maxInList(season) + 1;
            if (teamMatchMapper.countSeasonMatchNo(teamId, season, candidate) > 0) {
                return (int) (teamMatchMapper.maxSeasonMatchNo(teamId, season) + 1);
            }
            return candidate;
        } catch (RuntimeException ex) {
            log.warn("season match no next failed, teamId={}, season={}", teamId, season, ex);
            return (int) (teamMatchMapper.maxSeasonMatchNo(teamId, season) + 1);
        }
    }

    private void ensureSeeded(Long teamId, String season) {
        try {
            String key = key(season);
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                return;
            }
            List<Integer> active = teamMatchMapper.activeSeasonMatchNos(
                    teamId, season, MatchStatus.CANCELLED.code());
            if (active == null || active.isEmpty()) {
                return;
            }
            Object[] values = active.stream().map(String::valueOf).toArray();
            redis.execute(SEED_SCRIPT, List.of(key), values);
        } catch (RuntimeException ex) {
            log.warn("season match no seed failed, teamId={}, season={}", teamId, season, ex);
        }
    }

    private int maxInList(String season) {
        List<String> values = redis.opsForList().range(key(season), 0, -1);
        int max = 0;
        if (values != null) {
            for (String value : values) {
                try {
                    max = Math.max(max, Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                    // 列表只应包含数字；脏值忽略，避免阻塞分配。
                }
            }
        }
        return max;
    }

    /** 事务提交后写入，回滚不写 Redis，避免 list 与数据库漂移。 */
    public void recordAfterCommit(Long teamId, String season, int no) {
        afterCommit(() -> record(teamId, season, no));
    }

    /** 事务提交后移除，回滚不写 Redis。 */
    public void forgetAfterCommit(Long teamId, String season, int no) {
        afterCommit(() -> forget(teamId, season, no));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    /** 比赛取消后移除该场次号；失败不影响取消事务。 */
    public void forget(Long teamId, String season, int no) {
        try {
            redis.opsForList().remove(key(season), 0, String.valueOf(no));
        } catch (RuntimeException ex) {
            log.warn("season match no forget failed, teamId={}, season={}, no={}",
                    teamId, season, no, ex);
        }
    }

    /** 比赛创建/转入新赛季后写入；重复调用幂等。 */
    public void record(Long teamId, String season, int no) {
        ensureSeeded(teamId, season);
        String key = key(season);
        String value = String.valueOf(no);
        try {
            if (!redis.opsForList().range(key, 0, -1).contains(value)) {
                redis.opsForList().rightPush(key, value);
            }
        } catch (RuntimeException ex) {
            log.warn("season match no record failed, teamId={}, season={}, no={}",
                    teamId, season, no, ex);
        }
    }

    static String key(String season) {
        return KEY_PREFIX + season;
    }
}
