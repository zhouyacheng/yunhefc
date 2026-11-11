package com.yunhefc.attendance.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.attendance.domain.Attendance;
import com.yunhefc.attendance.domain.AttendanceSource;
import com.yunhefc.attendance.domain.AttendanceStatus;
import com.yunhefc.attendance.domain.CheckInWindow;
import com.yunhefc.attendance.dto.AttendanceDtos.AttendanceView;
import com.yunhefc.attendance.infrastructure.AttendanceMapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.registration.RegistrationSnapshot;
import com.yunhefc.contracts.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttendanceApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceApplicationService.class);

    private final AttendanceMapper attendanceMapper;
    private final MatchReadPort matchReadPort;
    private final PlayerReadPort playerReadPort;
    private final RegistrationReadPort registrationReadPort;
    private final Clock clock;

    /**
     * ATT-01：仅本场 CONFIRMED 报名且处于签到窗口内可签；已有记录原样返回，
     * 管理员/系统记录优先于自助签到，重复调用幂等。
     */
    @Transactional
    public AttendanceView checkIn(Long matchId) {
        AuthPrincipal principal = CurrentUser.principal();
        MatchSnapshot match = requireMatch(matchId, principal.teamId());
        if (match.cancelledOrPostponed()) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "比赛已取消或已延期，不能签到");
        }

        PlayerSnapshot player = playerReadPort.findByUserId(principal.userId())
                .orElseThrow(() -> violation("无球员档案，不能签到"));

        Optional<Attendance> existing = findByMatchAndPlayer(matchId, player.id());
        if (existing.isPresent()) {
            return toView(existing.get());
        }

        RegistrationSnapshot registration = registrationReadPort
                .findActiveByUser(matchId, principal.userId())
                .filter(RegistrationSnapshot::confirmed)
                .orElseThrow(() -> violation("仅已确认报名的球员可以签到"));
        if (!CheckInWindow.isOpen(match.startAt(), Instant.now(clock))) {
            throw violation("不在签到窗口内（开赛前 4 小时至比赛当天 23:59）");
        }

        Attendance attendance = new Attendance();
        attendance.setMatchId(matchId);
        attendance.setPlayerId(registration.playerId());
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setSource(AttendanceSource.SELF);
        attendance.setRecordedBy(principal.userId());
        attendance.setRecordedAt(Instant.now(clock));
        try {
            attendanceMapper.insert(attendance);
        } catch (DuplicateKeyException ex) {
            // 并发/重试下另一请求已写入：返回同一条记录，保证签到幂等。
            log.debug("attendance unique key conflict, matchId={}, playerId={}",
                    matchId, registration.playerId(), ex);
            return findByMatchAndPlayer(matchId, registration.playerId())
                    .map(AttendanceApplicationService::toView)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BUSINESS_RULE_VIOLATION, "签到失败，请稍后重试"));
        }
        log.info("attendance checked in, attendanceId={}, matchId={}, playerId={}",
                attendance.getId(), matchId, registration.playerId());
        return toView(attendance);
    }

    /**
     * ATT-02：本人考勤；登录即可读，无记录返回 null。
     */
    @Transactional(readOnly = true)
    public AttendanceView myAttendance(Long matchId) {
        AuthPrincipal principal = CurrentUser.principal();
        requireMatch(matchId, principal.teamId());
        return playerReadPort.findByUserId(principal.userId())
                .flatMap(player -> findByMatchAndPlayer(matchId, player.id()))
                .map(AttendanceApplicationService::toView)
                .orElse(null);
    }

    private MatchSnapshot requireMatch(Long matchId, Long teamId) {
        return matchReadPort.findById(matchId)
                .filter(match -> match.teamId().equals(teamId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));
    }

    private Optional<Attendance> findByMatchAndPlayer(Long matchId, Long playerId) {
        return Optional.ofNullable(attendanceMapper.selectOne(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getMatchId, matchId)
                .eq(Attendance::getPlayerId, playerId)
                .last("LIMIT 1")));
    }

    private static AttendanceView toView(Attendance attendance) {
        return new AttendanceView(
                attendance.getId(),
                attendance.getPlayerId(),
                attendance.getStatus().code(),
                attendance.getStatus().label(),
                attendance.getSource().code(),
                attendance.getSource().label(),
                attendance.getRecordedAt());
    }

    private static BusinessException violation(String message) {
        return new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
