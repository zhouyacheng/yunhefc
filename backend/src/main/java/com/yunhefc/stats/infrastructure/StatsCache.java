package com.yunhefc.stats.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.stats.application.PlayerStatsRow;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 赛季统计缓存：汇总任务刷新，读接口缓存未命中回源汇总表；缓存故障降级到数据库。
 */
@Component
@RequiredArgsConstructor
public class StatsCache {

    private static final Logger log = LoggerFactory.getLogger(StatsCache.class);
    private static final String KEY_PREFIX = "yunhefc:stats:";
    private static final TypeReference<List<PlayerStatsRow>> ROWS_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public Optional<List<PlayerStatsRow>> read(Long teamId, String season) {
        try {
            String payload = redis.opsForValue().get(key(teamId, season));
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(payload, ROWS_TYPE));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("stats cache read failed, teamId={}, season={}", teamId, season, ex);
            return Optional.empty();
        }
    }

    public void write(Long teamId, String season, List<PlayerStatsRow> rows) {
        try {
            redis.opsForValue().set(key(teamId, season), objectMapper.writeValueAsString(rows));
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("stats cache write failed, teamId={}, season={}", teamId, season, ex);
        }
    }

    private static String key(Long teamId, String season) {
        return KEY_PREFIX + teamId + ":" + season;
    }
}
