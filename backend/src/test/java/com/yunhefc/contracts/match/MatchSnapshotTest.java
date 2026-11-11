package com.yunhefc.contracts.match;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class MatchSnapshotTest {

    private static final Instant START = Instant.parse("2026-03-01T04:00:00Z");

    @Test
    void effectiveSeasonPrefersExplicitMatchSeason() {
        assertThat(snapshot("2025", START).effectiveSeason()).isEqualTo("2025");
    }

    @Test
    void effectiveSeasonFallsBackToStartYearInShanghai() {
        assertThat(snapshot(null, START).effectiveSeason()).isEqualTo("2026");
        assertThat(snapshot("", START).effectiveSeason()).isEqualTo("2026");
        // UTC 2025-12-31T16:00Z 在上海已是 2026 年。
        assertThat(snapshot(null, Instant.parse("2025-12-31T16:00:00Z")).effectiveSeason())
                .isEqualTo("2026");
    }

    @Test
    void effectiveSeasonIsNullWithoutSeasonOrStart() {
        assertThat(snapshot(null, null).effectiveSeason()).isNull();
    }

    private static MatchSnapshot snapshot(String season, Instant startAt) {
        return new MatchSnapshot(1L, 1L, "测试", startAt, startAt, null, null, 30, 4, 2,
                "场地", 4, "橙队", "兰队", null, season, 1, null, true);
    }
}
