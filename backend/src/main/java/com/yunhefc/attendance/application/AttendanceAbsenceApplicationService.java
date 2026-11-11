package com.yunhefc.attendance.application;

import com.yunhefc.attendance.domain.AttendanceSource;
import com.yunhefc.attendance.domain.AttendanceStatus;
import com.yunhefc.attendance.infrastructure.AttendanceMapper;
import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

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
    private final RegistrationReadPort registrationReadPort;
    private final Clock clock;

    /**
     * @return 本次新增的缺勤记录数；重复执行返回 0。
     */
    public int markDueAbsences() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(ABSENCE_DELAY);
        int created = 0;
        for (MatchSnapshot candidate : matchReadPort.findFinishedBefore(cutoff)) {
            try {
                // 候选在扫描后可能被取消/延期；逐场当前读后复核，避免给作废场次记缺勤。
                MatchSnapshot match = matchReadPort.findById(candidate.id()).orElse(null);
                if (match == null || !match.finished() || !isDue(match, cutoff)) {
                    continue;
                }
                Set<Long> missing = new HashSet<>(
                        registrationReadPort.findConfirmedPlayerIds(match.id()));
                if (missing.isEmpty()) {
                    continue;
                }
                missing.removeAll(attendanceMapper.selectPlayerIdsByMatch(match.id()));
                if (missing.isEmpty()) {
                    continue;
                }
                created += attendanceMapper.insertAbsences(match.id(), missing,
                        AttendanceStatus.ABSENT.code(), AttendanceSource.SYSTEM.code(), now);
            } catch (RuntimeException ex) {
                // 单场失败不影响同一轮其他比赛；下一轮按唯一键幂等重试。
                log.warn("Absence marking failed, matchId={}", candidate.id(), ex);
            }
        }
        log.debug("Absence marking finished, created={}", created);
        return created;
    }

    private static boolean isDue(MatchSnapshot match, Instant cutoff) {
        Instant actualEnd = match.endAt();
        if (actualEnd == null) {
            return false;
        }
        // 仅提前结束（status_updated_at 早于 end_at）用实际结束时间；延期完成等仍按 end_at。
        if (match.statusUpdatedAt() != null && match.statusUpdatedAt().isBefore(actualEnd)) {
            actualEnd = match.statusUpdatedAt();
        }
        return !actualEnd.isAfter(cutoff);
    }
}
