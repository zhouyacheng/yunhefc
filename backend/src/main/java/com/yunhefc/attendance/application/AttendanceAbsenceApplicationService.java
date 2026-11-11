package com.yunhefc.attendance.application;

import com.yunhefc.attendance.infrastructure.AttendanceMapper;
import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 赛后缺勤判定：已结束且实际结束 +6h 已到，给无考勤记录的 CONFIRMED 报名记 ABSENT/SYSTEM。
 * 实际结束时间优先 status_updated_at（提前结束），否则 end_at；重复执行不覆盖已有记录。
 */
@Service
@RequiredArgsConstructor
public class AttendanceAbsenceApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceAbsenceApplicationService.class);
    private static final Duration ABSENCE_DELAY = Duration.ofHours(6);

    private final AttendanceMapper attendanceMapper;
    private final MatchReadPort matchReadPort;
    private final Clock clock;

    /**
     * @return 本次新增的缺勤记录数；重复执行返回 0。
     */
    public int markDueAbsences() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(ABSENCE_DELAY);
        int created = 0;
        for (Long matchId : attendanceMapper.selectAbsenceDueMatchIds(cutoff)) {
            try {
                MatchSnapshot match = matchReadPort.findById(matchId).orElse(null);
                if (match == null || !match.finished() || !isDue(match, cutoff)) {
                    continue;
                }
                created += attendanceMapper.insertSystemAbsences(matchId, now);
            } catch (RuntimeException ex) {
                // 单场失败不影响同一轮其他比赛；下一轮按唯一键幂等重试。
                log.warn("Absence marking failed, matchId={}", matchId, ex);
            }
        }
        if (created > 0) {
            log.info("Marked absences automatically, count={}", created);
        }
        return created;
    }

    private static boolean isDue(MatchSnapshot match, Instant cutoff) {
        Instant actualEnd = match.statusUpdatedAt() != null ? match.statusUpdatedAt() : match.endAt();
        return actualEnd != null && !actualEnd.isAfter(cutoff);
    }
}
