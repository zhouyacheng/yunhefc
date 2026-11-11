package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.attendance.application.AttendanceAbsenceApplicationService;
import com.yunhefc.attendance.infrastructure.AttendanceAbsenceJob;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

class AttendanceAbsenceIT extends IntegrationTestSupport {

    @Autowired
    private AttendanceAbsenceApplicationService absenceService;

    @Autowired
    private ApplicationContext context;

    @Test
    void marksAbsentForConfirmedRegistrationsWithoutAttendanceUsingStatusUpdatedAt() {
        Long matchId = insertMatch("M05缺勤提前结束", 4,
                Instant.now().minus(7, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS));
        Long absent = insertPlayer("13900003601", "缺勤球员一", 1);
        Long present = insertPlayer("13900003602", "缺勤球员二", 1);
        Long waitlisted = insertPlayer("13900003603", "缺勤候补", 1);
        Long cancelled = insertPlayer("13900003604", "缺勤已取消", 1);
        register(matchId, absent, 0);
        register(matchId, present, 0);
        register(matchId, waitlisted, 1);
        register(matchId, cancelled, 2);
        insertAttendance(matchId, present, 0, 0);

        assertThat(absenceService.markDueAbsences()).isEqualTo(1);

        assertAttendance(matchId, absent, 1, 2, null);
        assertAttendance(matchId, present, 0, 0, null);
        assertThat(attendanceCount(matchId, waitlisted)).isZero();
        assertThat(attendanceCount(matchId, cancelled)).isZero();
    }

    @Test
    void fallsBackToEndAtWhenStatusUpdatedAtIsMissing() {
        Long due = insertMatch("M05缺勤无状态时间到期", 4, null,
                Instant.now().minus(7, ChronoUnit.HOURS));
        Long notDue = insertMatch("M05缺勤无状态时间未到期", 4, null,
                Instant.now().minus(5, ChronoUnit.HOURS));
        Long duePlayer = insertPlayer("13900003611", "缺勤回退一", 1);
        Long notDuePlayer = insertPlayer("13900003612", "缺勤回退二", 1);
        register(due, duePlayer, 0);
        register(notDue, notDuePlayer, 0);

        assertThat(absenceService.markDueAbsences()).isEqualTo(1);

        assertAttendance(due, duePlayer, 1, 2, null);
        assertThat(attendanceCount(notDue, notDuePlayer)).isZero();
    }

    @Test
    void usesStatusUpdatedAtOnlyForEarlyFinish() {
        Long lateFinish = insertMatch("M05缺勤延迟完赛", 4,
                Instant.now().minus(5, ChronoUnit.HOURS),
                Instant.now().minus(20, ChronoUnit.HOURS));
        Long earlyDue = insertMatch("M05缺勤提前完赛", 4,
                Instant.now().minus(7, ChronoUnit.HOURS),
                Instant.now().plus(10, ChronoUnit.HOURS));
        Long earlyNotDue = insertMatch("M05缺勤提前未完赛", 4,
                Instant.now().minus(5, ChronoUnit.HOURS),
                Instant.now().plus(10, ChronoUnit.HOURS));
        Long first = insertPlayer("13900003621", "缺勤优先一", 1);
        Long second = insertPlayer("13900003622", "缺勤优先二", 1);
        Long third = insertPlayer("13900003623", "缺勤优先三", 1);
        register(lateFinish, first, 0);
        register(earlyDue, second, 0);
        register(earlyNotDue, third, 0);

        assertThat(absenceService.markDueAbsences()).isEqualTo(2);

        // 晚于排期结束才标记完赛：按 end_at 计算，已到期。
        assertAttendance(lateFinish, first, 1, 2, null);
        assertAttendance(earlyDue, second, 1, 2, null);
        assertThat(attendanceCount(earlyNotDue, third)).isZero();
    }

    @Test
    void skipsCancelledPostponedAndUnfinishedMatches() {
        Instant longAgo = Instant.now().minus(20, ChronoUnit.HOURS);
        Long cancelled = insertMatch("M05缺勤跳过取消", 6, longAgo, longAgo);
        Long postponed = insertMatch("M05缺勤跳过延期", 5, longAgo, longAgo);
        Long inProgress = insertMatch("M05缺勤跳过进行中", 3, longAgo, longAgo);
        Long signupOpen = insertMatch("M05缺勤跳过报名中", 0, longAgo, longAgo);
        Long player = insertPlayer("13900003631", "缺勤跳过球员", 1);
        for (Long matchId : new Long[]{cancelled, postponed, inProgress, signupOpen}) {
            register(matchId, player, 0);
        }

        assertThat(absenceService.markDueAbsences()).isZero();

        for (Long matchId : new Long[]{cancelled, postponed, inProgress, signupOpen}) {
            assertThat(attendanceCount(matchId, player)).isZero();
        }
    }

    @Test
    void repeatedRunsAreIdempotentAndDoNotOverwriteExistingRecords() {
        Long matchId = insertMatch("M05缺勤幂等", 4,
                Instant.now().minus(7, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS));
        Long missing = insertPlayer("13900003641", "缺勤幂等一", 1);
        Long adminMarked = insertPlayer("13900003642", "缺勤幂等二", 1);
        register(matchId, missing, 0);
        register(matchId, adminMarked, 0);
        insertAttendance(matchId, adminMarked, 1, 1);

        assertThat(absenceService.markDueAbsences()).isEqualTo(1);
        assertAttendance(matchId, missing, 1, 2, null);
        assertAttendance(matchId, adminMarked, 1, 1, 1L);
        assertThat(attendanceTotal(matchId)).isEqualTo(2);

        assertThat(absenceService.markDueAbsences()).isZero();
        assertThat(attendanceTotal(matchId)).isEqualTo(2);
    }

    @Test
    void quartzJobRunsEveryTenMinutes() {
        JobDetail detail = context.getBean("attendanceAbsenceJobDetail", JobDetail.class);
        assertThat(detail.getJobClass()).isEqualTo(AttendanceAbsenceJob.class);
        assertThat(detail.getKey()).isEqualTo(JobKey.jobKey("attendanceAbsenceJob", "yunhefc"));

        Trigger trigger = context.getBean("attendanceAbsenceTrigger", Trigger.class);
        assertThat(trigger).isInstanceOf(CronTrigger.class);
        assertThat(((CronTrigger) trigger).getCronExpression()).isEqualTo("0 */10 * * * ?");
        assertThat(trigger.getKey())
                .isEqualTo(TriggerKey.triggerKey("attendanceAbsenceTrigger", "yunhefc"));
        assertThat(trigger.getJobKey()).isEqualTo(detail.getKey());
    }

    private void assertAttendance(Long matchId, Long playerId, int status, int source,
                                  Long recordedBy) {
        assertThat(jdbc.queryForObject("""
                SELECT status FROM attendance WHERE match_id = ? AND player_id = ?
                """, Integer.class, matchId, playerId)).isEqualTo(status);
        assertThat(jdbc.queryForObject("""
                SELECT source FROM attendance WHERE match_id = ? AND player_id = ?
                """, Integer.class, matchId, playerId)).isEqualTo(source);
        assertThat(jdbc.queryForObject("""
                SELECT recorded_by FROM attendance WHERE match_id = ? AND player_id = ?
                """, Long.class, matchId, playerId)).isEqualTo(recordedBy);
    }

    private int attendanceCount(Long matchId, Long playerId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM attendance WHERE match_id = ? AND player_id = ?
                """, Integer.class, matchId, playerId);
    }

    private int attendanceTotal(Long matchId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE match_id = ?", Integer.class, matchId);
    }

    private void register(Long matchId, Long playerId, int status) {
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, 1, ?, NOW(3))
                """, matchId, playerId, status);
    }

    private void insertAttendance(Long matchId, Long playerId, int status, int source) {
        jdbc.update("""
                INSERT INTO attendance (match_id, player_id, status, source, recorded_by, recorded_at)
                VALUES (?, ?, ?, ?, ?, NOW(3))
                """, matchId, playerId, status, source, source == 1 ? 1 : null);
    }

    private Long insertPlayer(String phone, String name, int type) {
        Long role = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class,
                type == 1 ? "PLAYER" : "GUEST");
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, role);
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, ?, 2, '[]', 3)
                """, userId, name, type);
        return jdbc.queryForObject(
                "SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private long nextMatchNo = 910_000;

    private Long insertMatch(String title, int status, Instant statusUpdatedAt, Instant endAt) {
        long matchNo = nextMatchNo++;
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status,
                    manual_status, capacity, season_match_no, total_match_no, status_updated_at,
                    created_by)
                VALUES (1, ?, 1, ?, ?, ?, ?, '测试场地', '2099', '橙队', '兰队', ?, 1, 30, ?, ?, ?,
                    1)
                """, title,
                Timestamp.from(endAt.minus(2, ChronoUnit.HOURS)),
                Timestamp.from(endAt),
                Timestamp.from(endAt.minus(10, ChronoUnit.HOURS)),
                Timestamp.from(endAt.minus(4, ChronoUnit.HOURS)),
                status, matchNo, matchNo,
                statusUpdatedAt == null ? null : Timestamp.from(statusUpdatedAt));
        return jdbc.queryForObject("SELECT id FROM team_match WHERE title = ?", Long.class, title);
    }
}
