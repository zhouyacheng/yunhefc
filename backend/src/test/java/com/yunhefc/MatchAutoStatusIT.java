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
    }

    @Test
    void catchesUpToHighestReachedStatusWhenTicksWereMissed() {
        Instant now = Instant.now();
        Long missed = insertMatch("M03自动补跑", 0, false,
                now.minusSeconds(14_400), now.minusSeconds(10_800),
                now.minusSeconds(7_200), now.minusSeconds(60));

        assertThat(autoStatusService.advance()).isEqualTo(1);
        assertState(missed, 4, 1, true);
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
    }

    @Test
    void neverMovesBackwards() {
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
    }

    @Test
    void repeatedRunsHaveNoSideEffects() {
        Instant now = Instant.now();
        Long matchId = insertMatch("M03自动幂等", 0, false,
                now.minusSeconds(14_400), now.minusSeconds(10_800),
                now.minusSeconds(7_200), now.minusSeconds(60));

        assertThat(autoStatusService.advance()).isEqualTo(1);
        Timestamp firstUpdatedAt = statusUpdatedAt(matchId);

        assertThat(autoStatusService.advance()).isZero();
        assertState(matchId, 4, 1, true);
        assertThat(statusUpdatedAt(matchId)).isEqualTo(firstUpdatedAt);
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
