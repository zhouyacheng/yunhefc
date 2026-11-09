package com.yunhefc.contracts.match;

import java.time.Instant;

/**
 * 跨模块读取的比赛快照。
 */
public record MatchSnapshot(
        Long id,
        Long teamId,
        String title,
        Instant startAt,
        Instant endAt,
        String venue,
        Integer status,
        String homeTeamName,
        String awayTeamName) {
}
