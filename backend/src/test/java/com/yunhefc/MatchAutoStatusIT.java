package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.match.application.MatchAutoStatusApplicationService;
import com.yunhefc.match.infrastructure.MatchAutoStatusJob;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.List;

class MatchAutoStatusIT extends IntegrationTestSupport {

    @Autowired
    private MatchAutoStatusApplicationService autoStatusService;

    @Autowired
    private ApplicationContext context;

    private final List<Long> createdMatchIds = new ArrayList<>();
    private int nextMatchNo = 1;

    @AfterEach
    void cleanup() {
        for (Long matchId : createdMatchIds) {
            jdbc.update("DELETE FROM team_match WHERE id = ?", matchId);
        }
    }

    @Test
    void advancesAtEachTimeBoundary() {
        Instant now = Instant.now();
        Long beforeDeadline = insertMatch("M03自动未到截止", 0, false,
                now.plusSeconds(3_600), now.plusSeconds(7_200),
                now.plusSeconds(10_800), now.plusSeconds(21_600));
        Long afterDeadline = insertMatch("M03自动到截止", 0, false,
                now.minusSeconds(3_600), now.plusSeconds(3_600),
                now.plusSeconds(7_200), now.plusSeconds(18_000));
        Long afterLock = insertMatch("M03自动到锁定", 1, false,
                now.minusSeconds(7_200), now.minusSeconds(60),
                now.plusSeconds(3_600), now.plusSeconds(14_400));
        Long afterStart = insertMatch("M03自动到开赛", 2, false,
                now.minusSeconds(10_800), now.minusSeconds(7_200),
                now.minusSeconds(60), now.plusSeconds(10_800));
        Long afterEnd = insertMatch("M03自动到结束", 3, false,
                now.minusSeconds(14_400), now.minusSeconds(10_800),
                now.minusSeconds(7_200), now.minusSeconds(60));

        assertThat(autoStatusService.advance()).isEqualTo(4);

        assertState(beforeDeadline, 0, 0, false);
        assertState(afterDeadline, 1, 1, true);
        assertState(afterLock, 2, 1, true);
        assertState(afterStart, 3, 1, true);
        assertState(afterEnd, 4, 1, true);

        assertThat(jdbc.queryForObject(
                "SELECT status_updated_by FROM team_match WHERE id = ?", Long.class, afterEnd)).isNull();

        assertThat(autoAuditCount(beforeDeadline)).isZero();
        assertThat(autoAuditCount(afterDeadline)).isEqualTo(1);
        assertThat(autoAuditCount(afterLock)).isEqualTo(1);
        assertThat(autoAuditCount(afterStart)).isEqualTo(1);
        assertThat(autoAuditCount(afterEnd)).isEqualTo(1);
        assertThat(auditStatus(afterDeadline, true)).isZero();
        assertThat(auditStatus(afterDeadline, false)).isEqualTo(1);
        assertThat(auditStatus(afterLock, true)).isEqualTo(1);
        assertThat(auditStatus(afterLock, false)).isEqualTo(2);
        assertThat(auditStatus(afterStart, true)).isEqualTo(2);
        assertThat(auditStatus(afterStart, false)).isEqualTo(3);
        assertThat(auditStatus(afterEnd, true)).isEqualTo(3);
        assertThat(auditStatus(afterEnd, false)).isEqualTo(4);

        assertThat(auditField(afterEnd, "$.action")).isEqualTo("MATCH_AUTO_STATUS_CHANGE");
        assertThat(auditField(afterEnd, "$.actorName")).isEqualTo("系统");
        assertThat(auditField(afterEnd, "$.teamId")).isEqualTo("1");
        assertThat(auditField(afterEnd, "$.targetType")).isEqualTo("MATCH");
        assertThat(auditField(afterEnd, "$.targetId")).isEqualTo(String.valueOf(afterEnd));
        assertThat(auditFieldType(afterEnd, "$.actorUserId")).isEqualTo("NULL");
        assertThat(auditSnapshotFieldType(afterEnd, true, "statusUpdatedAt")).isEqualTo("NULL");
        assertThat(auditSnapshotFieldType(afterEnd, false, "statusUpdatedAt")).isEqualTo("STRING");
        assertThat(auditSnapshotFieldType(afterEnd, false, "statusUpdatedBy")).isEqualTo("NULL");
        assertThat(Instant.parse(auditStatusUpdatedAt(afterEnd)))
                .isEqualTo(statusUpdatedAt(afterEnd).toInstant());
        assertThat(auditAggregateType(afterEnd)).isEqualTo("AUDIT");
        assertThat(auditTeamId(afterEnd)).isEqualTo(1L);
    }

    @Test
    void catchesUpToHighestReachedStatusWhenTicksWereMissed() {
        Instant now = Instant.now();
        Long missed = insertMatch("M03自动补跑", 0, false,
                now.minusSeconds(14_400), now.minusSeconds(10_800),
                now.minusSeconds(7_200), now.minusSeconds(60));

        assertThat(autoStatusService.advance()).isEqualTo(1);
        assertState(missed, 4, 1, true);
        assertThat(autoAuditCount(missed)).isEqualTo(1);
        assertThat(auditStatus(missed, true)).isZero();
        assertThat(auditStatus(missed, false)).isEqualTo(4);
    }

    @Test
    void skipsManualCancelledPostponedAndFinishedMatches() {
        Instant now = Instant.now();
        Long manual = insertMatch("M03自动跳过手动", 0, true,
                now.minusSeconds(60), now.plusSeconds(3_600),
                now.plusSeconds(7_200), now.plusSeconds(18_000));
        Long cancelled = insertMatch("M03自动跳过取消", 6, false,
                now.minusSeconds(14_400), now.minusSeconds(10_800),
                now.minusSeconds(7_200), now.minusSeconds(60));
        Long postponed = insertMatch("M03自动跳过延期", 5, false,
                now.minusSeconds(14_400), now.minusSeconds(10_800),
                now.minusSeconds(7_200), now.minusSeconds(60));
        Long finished = insertMatch("M03自动跳过结束", 4, false,
                now.minusSeconds(14_400), now.minusSeconds(10_800),
                now.minusSeconds(7_200), now.minusSeconds(60));

        assertThat(autoStatusService.advance()).isZero();

        assertState(manual, 0, 0, false);
        assertState(cancelled, 6, 0, false);
        assertState(postponed, 5, 0, false);
        assertState(finished, 4, 0, false);
        for (Long matchId : List.of(manual, cancelled, postponed, finished)) {
            assertThat(autoAuditCount(matchId)).isZero();
        }
    }

    @Test
    void doesNotAdvanceBeforeTimeBoundaries() {
        Instant now = Instant.now();
        Long locked = insertMatch("M03自动不回退锁定", 2, false,
                now.plusSeconds(3_600), now.plusSeconds(7_200),
                now.plusSeconds(10_800), now.plusSeconds(21_600));
        Long inProgress = insertMatch("M03自动不回退进行中", 3, false,
                now.plusSeconds(3_600), now.plusSeconds(7_200),
                now.plusSeconds(10_800), now.plusSeconds(21_600));

        assertThat(autoStatusService.advance()).isZero();

        assertState(locked, 2, 0, false);
        assertState(inProgress, 3, 0, false);
        assertThat(autoAuditCount(locked)).isZero();
        assertThat(autoAuditCount(inProgress)).isZero();
    }

    @Test
    void nonAdvancingRowsWriteNoAudit() {
        Instant now = Instant.now();
        Long locked = insertMatch("M03自动候选无推进锁定", 2, false,
                now.minusSeconds(60), now.plusSeconds(3_600),
                now.plusSeconds(7_200), now.plusSeconds(18_000));
        Long inProgress = insertMatch("M03自动候选无推进进行中", 3, false,
                now.minusSeconds(60), now.minusSeconds(30),
                now.plusSeconds(3_600), now.plusSeconds(18_000));

        assertThat(autoStatusService.advance()).isZero();

        assertState(locked, 2, 0, false);
        assertState(inProgress, 3, 0, false);
        assertThat(autoAuditCount(locked)).isZero();
        assertThat(autoAuditCount(inProgress)).isZero();
    }

    @Test
    void repeatedRunsHaveNoSideEffects() {
        Instant now = Instant.now();
        Long matchId = insertMatch("M03自动幂等", 0, false,
                now.minusSeconds(14_400), now.minusSeconds(10_800),
                now.minusSeconds(7_200), now.minusSeconds(60));

        assertThat(autoStatusService.advance()).isEqualTo(1);
        Timestamp firstUpdatedAt = statusUpdatedAt(matchId);
        assertThat(autoAuditCount(matchId)).isEqualTo(1);
        int firstAuditTotal = auditTotal();

        assertThat(autoStatusService.advance()).isZero();
        assertState(matchId, 4, 1, true);
        assertThat(statusUpdatedAt(matchId)).isEqualTo(firstUpdatedAt);
        assertThat(autoAuditCount(matchId)).isEqualTo(1);
        assertThat(auditTotal()).isEqualTo(firstAuditTotal);
    }

    @Test
    void quartzJobRunsEveryMinute() {
        JobDetail detail = context.getBean("matchAutoStatusJobDetail", JobDetail.class);
        assertThat(detail.getJobClass()).isEqualTo(MatchAutoStatusJob.class);
        assertThat(detail.getKey()).isEqualTo(JobKey.jobKey("matchAutoStatusJob", "yunhefc"));

        Trigger trigger = context.getBean("matchAutoStatusTrigger", Trigger.class);
        assertThat(trigger).isInstanceOf(CronTrigger.class);
        assertThat(((CronTrigger) trigger).getCronExpression()).isEqualTo("0 * * * * ?");
        assertThat(trigger.getKey()).isEqualTo(TriggerKey.triggerKey("matchAutoStatusTrigger", "yunhefc"));
        assertThat(trigger.getJobKey()).isEqualTo(detail.getKey());
    }

    private void assertState(Long matchId, int status, int version, boolean statusUpdated) {
        assertThat(jdbc.queryForObject(
                "SELECT status FROM team_match WHERE id = ?", Integer.class, matchId)).isEqualTo(status);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM team_match WHERE id = ?", Integer.class, matchId)).isEqualTo(version);
        assertThat(statusUpdatedAt(matchId) != null).isEqualTo(statusUpdated);
    }

    private Timestamp statusUpdatedAt(Long matchId) {
        return jdbc.queryForObject(
                "SELECT status_updated_at FROM team_match WHERE id = ?", Timestamp.class, matchId);
    }

    private int autoAuditCount(Long matchId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_AUTO_STATUS_CHANGE'
                """, Integer.class, matchId);
    }

    private int auditTotal() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'AUDIT_RECORDED'", Integer.class);
    }

    private String auditField(Long matchId, String jsonPath) {
        return jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, ?))
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_AUTO_STATUS_CHANGE'
                ORDER BY id ASC LIMIT 1
                """, String.class, jsonPath, matchId);
    }

    private String auditFieldType(Long matchId, String jsonPath) {
        return jdbc.queryForObject("""
                SELECT JSON_TYPE(JSON_EXTRACT(payload_json, ?))
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_AUTO_STATUS_CHANGE'
                ORDER BY id ASC LIMIT 1
                """, String.class, jsonPath, matchId);
    }

    private int auditStatus(Long matchId, boolean before) {
        String column = before ? "beforeJson" : "afterJson";
        return jdbc.queryForObject("""
                SELECT JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.%s')), '$.status')
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_AUTO_STATUS_CHANGE'
                ORDER BY id ASC LIMIT 1
                """.formatted(column), Integer.class, matchId);
    }

    private String auditStatusUpdatedAt(Long matchId) {
        return jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                    JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')), '$.statusUpdatedAt'))
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_AUTO_STATUS_CHANGE'
                ORDER BY id ASC LIMIT 1
                """, String.class, matchId);
    }

    private String auditSnapshotFieldType(Long matchId, boolean before, String field) {
        String column = before ? "beforeJson" : "afterJson";
        return jdbc.queryForObject("""
                SELECT JSON_TYPE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.%s')), '$.%s'))
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_AUTO_STATUS_CHANGE'
                ORDER BY id ASC LIMIT 1
                """.formatted(column, field), String.class, matchId);
    }

    private String auditAggregateType(Long matchId) {
        return jdbc.queryForObject("""
                SELECT aggregate_type FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_AUTO_STATUS_CHANGE'
                LIMIT 1
                """, String.class, matchId);
    }

    private Long auditTeamId(Long matchId) {
        return jdbc.queryForObject("""
                SELECT team_id FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_AUTO_STATUS_CHANGE'
                LIMIT 1
                """, Long.class, matchId);
    }

    private Long insertMatch(String title, int status, boolean manualStatus,
                             Instant signupDeadline, Instant cancelLock,
                             Instant startAt, Instant endAt) {
        int matchNo = nextMatchNo++;
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status,
                    manual_status, capacity, season_match_no, total_match_no, created_by)
                VALUES (1, ?, 1, ?, ?, ?, ?, '测试场地', '2099', '橙队', '兰队', ?, ?, 30, ?, ?, 1)
                """,
                title, Timestamp.from(startAt), Timestamp.from(endAt),
                Timestamp.from(signupDeadline), Timestamp.from(cancelLock),
                status, manualStatus ? 1 : 0, matchNo, matchNo);
        Long matchId = jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = ?", Long.class, title);
        createdMatchIds.add(matchId);
        return matchId;
    }
}
