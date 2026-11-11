package com.yunhefc.attendance.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.attendance.domain.Attendance;
import com.yunhefc.attendance.domain.AttendanceSource;
import com.yunhefc.attendance.domain.AttendanceStatus;
import com.yunhefc.attendance.domain.CheckInWindow;
import com.yunhefc.attendance.dto.AttendanceDtos.AttendanceRow;
import com.yunhefc.attendance.dto.AttendanceDtos.AttendanceView;
import com.yunhefc.attendance.dto.AttendanceDtos.PlayerRef;
import com.yunhefc.attendance.infrastructure.AttendanceMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.registration.RegistrationSnapshot;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.contracts.security.UserAccountSnapshot;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceApplicationService.class);
    private static final int DISABLED_ACCOUNT = 2;

    private final AttendanceMapper attendanceMapper;
    private final MatchReadPort matchReadPort;
    private final PlayerReadPort playerReadPort;
    private final RegistrationReadPort registrationReadPort;
    private final UserAccountReadPort userAccountReadPort;
    private final AuditRecorder auditRecorder;
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

    /**
     * ATT-03/04：全队考勤分页，含临时到场；球员档案缺失的行跳过。
     */
    @Transactional(readOnly = true)
    public PageResponse<AttendanceRow> list(Long matchId, Integer status, long page, long pageSize) {
        PageQuery.of(page, pageSize);
        AuthPrincipal principal = CurrentUser.principal();
        requireMatch(matchId, principal.teamId());
        AttendanceStatus filter = status == null ? null : parseStatus(status);

        Page<Attendance> result = attendanceMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getMatchId, matchId)
                        .eq(filter != null, Attendance::getStatus, filter)
                        .orderByAsc(Attendance::getRecordedAt)
                        .orderByAsc(Attendance::getId));
        Map<Long, PlayerSnapshot> players = playerReadPort.findByIds(principal.teamId(),
                        result.getRecords().stream().map(Attendance::getPlayerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PlayerSnapshot::id, Function.identity()));
        List<AttendanceRow> items = result.getRecords().stream()
                .filter(attendance -> players.containsKey(attendance.getPlayerId()))
                .map(attendance -> toRow(attendance, players.get(attendance.getPlayerId())))
                .toList();
        return PageResponse.of(items, result.getTotal(), page, pageSize);
    }

    /**
     * ATT-05：管理员标记/修正正常或缺勤；可覆盖既有来源。
     */
    @Transactional
    public AttendanceView mark(Long matchId, Long playerId, Integer status) {
        AuthPrincipal principal = CurrentUser.principal();
        MatchSnapshot match = requireMatch(matchId, principal.teamId());
        requireAttendanceOpen(match);
        AttendanceStatus target = parseMarkStatus(status);
        PlayerSnapshot player = requireTeamPlayer(playerId, principal.teamId());

        Instant now = Instant.now(clock);
        Optional<Attendance> existing = findByMatchAndPlayer(matchId, player.id());
        Map<String, Object> before = existing.map(AttendanceApplicationService::auditSnapshot)
                .orElse(null);
        Attendance attendance = write(matchId, player.id(), target, AttendanceSource.ADMIN,
                principal.userId(), now, existing.orElse(null));
        auditRecorder.recordCurrent("ATTENDANCE_MARK", "ATTENDANCE", attendance.getId(),
                before, auditSnapshot(attendance));
        log.info("attendance marked, attendanceId={}, matchId={}, playerId={}, status={}",
                attendance.getId(), matchId, player.id(), target.code());
        return toView(attendance);
    }

    /**
     * ATT-06：补录未确认报名的本队未注销球员为临时到场；不占报名名额。
     */
    @Transactional
    public AttendanceView tempPresent(Long matchId, Long playerId) {
        AuthPrincipal principal = CurrentUser.principal();
        MatchSnapshot match = requireMatch(matchId, principal.teamId());
        requireAttendanceOpen(match);
        PlayerSnapshot player = requireTeamPlayer(playerId, principal.teamId());

        UserAccountSnapshot account = userAccountReadPort.find(player.userId())
                .orElseThrow(() -> violation("球员账号不存在或已注销"));
        if (account.status() == null || account.status() == DISABLED_ACCOUNT) {
            throw violation("球员账号不存在或已注销");
        }
        boolean confirmed = registrationReadPort.findActiveByUser(matchId, player.userId())
                .filter(RegistrationSnapshot::confirmed)
                .isPresent();
        if (confirmed) {
            throw violation("已确认报名的球员请使用签到或考勤标记");
        }

        Optional<Attendance> existing = findByMatchAndPlayer(matchId, player.id());
        if (existing.isPresent()
                && existing.get().getStatus() == AttendanceStatus.TEMP_PRESENT
                && existing.get().getSource() == AttendanceSource.ADMIN) {
            return toView(existing.get());
        }

        Instant now = Instant.now(clock);
        Map<String, Object> before = existing.map(AttendanceApplicationService::auditSnapshot)
                .orElse(null);
        Attendance attendance = write(matchId, player.id(), AttendanceStatus.TEMP_PRESENT,
                AttendanceSource.ADMIN, principal.userId(), now, existing.orElse(null));
        auditRecorder.recordCurrent("ATTENDANCE_TEMP", "ATTENDANCE", attendance.getId(),
                before, auditSnapshot(attendance));
        log.info("temporary attendance added, attendanceId={}, matchId={}, playerId={}",
                attendance.getId(), matchId, player.id());
        return toView(attendance);
    }

    /**
     * 先查再写；并发插入撞唯一键时回读并覆盖，保证同一球员只有一条记录。
     */
    private Attendance write(Long matchId, Long playerId, AttendanceStatus status,
                             AttendanceSource source, Long userId, Instant now,
                             Attendance existing) {
        Attendance attendance = existing != null ? existing : new Attendance();
        attendance.setMatchId(matchId);
        attendance.setPlayerId(playerId);
        attendance.setStatus(status);
        attendance.setSource(source);
        attendance.setRecordedBy(userId);
        attendance.setRecordedAt(now);
        if (existing != null) {
            attendanceMapper.updateById(attendance);
            return attendance;
        }
        try {
            attendanceMapper.insert(attendance);
            return attendance;
        } catch (DuplicateKeyException ex) {
            Attendance saved = findByMatchAndPlayer(matchId, playerId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.BUSINESS_RULE_VIOLATION, "考勤写入失败，请稍后重试"));
            saved.setStatus(status);
            saved.setSource(source);
            saved.setRecordedBy(userId);
            saved.setRecordedAt(now);
            attendanceMapper.updateById(saved);
            return saved;
        }
    }

    private void requireAttendanceOpen(MatchSnapshot match) {
        if (match.cancelledOrPostponed()) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "比赛已取消或已延期，不能记录考勤");
        }
    }

    private PlayerSnapshot requireTeamPlayer(Long playerId, Long teamId) {
        return playerReadPort.findById(playerId)
                .filter(player -> player.teamId().equals(teamId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "球员不存在"));
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

    private static AttendanceStatus parseStatus(Integer status) {
        try {
            return AttendanceStatus.of(status);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status 必须为 0–2");
        }
    }

    private static AttendanceStatus parseMarkStatus(Integer status) {
        if (status == null
                || (status != AttendanceStatus.PRESENT.code() && status != AttendanceStatus.ABSENT.code())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "status 必须为 0（正常）或 1（缺勤）");
        }
        return AttendanceStatus.of(status);
    }

    private static AttendanceRow toRow(Attendance attendance, PlayerSnapshot player) {
        Position position = Position.of(player.position());
        return new AttendanceRow(
                attendance.getId(),
                new PlayerRef(player.id(), player.name(), player.jersey(),
                        position.code(), position.label()),
                attendance.getStatus().code(),
                attendance.getStatus().label(),
                attendance.getSource().code(),
                attendance.getSource().label(),
                attendance.getRecordedAt());
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

    private static Map<String, Object> auditSnapshot(Attendance attendance) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("matchId", attendance.getMatchId());
        values.put("playerId", attendance.getPlayerId());
        values.put("status", attendance.getStatus().code());
        values.put("source", attendance.getSource().code());
        values.put("recordedBy", attendance.getRecordedBy());
        values.put("recordedAt", attendance.getRecordedAt());
        return values;
    }

    private static BusinessException violation(String message) {
        return new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
