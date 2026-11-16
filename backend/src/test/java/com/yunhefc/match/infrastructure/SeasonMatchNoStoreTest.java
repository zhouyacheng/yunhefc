package com.yunhefc.match.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeasonMatchNoStoreTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final TeamMatchMapper teamMatchMapper = mock(TeamMatchMapper.class);
    private final SeasonMatchNoStore store = new SeasonMatchNoStore(redis, teamMatchMapper);

    @Test
    void nextFallsBackToDatabaseWhenRedisIsUnavailable() {
        when(redis.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(redis.opsForList())
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(teamMatchMapper.maxSeasonMatchNo(1L, "2026")).thenReturn(7L);

        assertThat(store.next(1L, "2026")).isEqualTo(8);
    }

    @Test
    void writeFailuresDoNotPropagate() {
        when(redis.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(redis.opsForList())
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatCode(() -> {
            store.record(1L, "2026", 3);
            store.forget(1L, "2026", 3);
        }).doesNotThrowAnyException();
    }
}
