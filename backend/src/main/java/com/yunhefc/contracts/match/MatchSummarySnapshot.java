package com.yunhefc.contracts.match;

import java.time.Instant;

/**
 * 跨模块读取的比赛摘要快照；展示标签与结果文本由比赛域派生，避免调用方复制规则。
 */
public record MatchSummarySnapshot(
        Long id,
        Long teamId,
        String title,
        Integer type,
        String typeLabel,
        Instant startAt,
        Instant endAt,
        String venue,
        Integer round,
        String season,
        String homeTeamName,
        String awayTeamName,
        Integer ourSide,
        String ourSideLabel,
        Integer scoreHome,
        Integer scoreAway,
        String resultText,
        Integer status,
        String statusLabel,
        Integer totalMatchNo,
        Integer seasonMatchNo) {

    public boolean finishedOrCancelled() {
        return status != null && (status == MatchStatusCodes.FINISHED
                || status == MatchStatusCodes.CANCELLED);
    }
}
