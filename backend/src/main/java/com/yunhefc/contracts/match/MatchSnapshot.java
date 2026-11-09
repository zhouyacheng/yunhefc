package com.yunhefc.contracts.match;

import java.time.LocalDateTime;

/**
 * 跨模块读取的比赛快照。
 */
public record MatchSnapshot(
        Long id,
        Long teamId,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String venue,
        Integer status,
        String homeTeamName,
        String awayTeamName) {
}
