package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhefc.contracts.attendance.AttendanceReadPort;
import com.yunhefc.stats.application.PlayerStatsRow;
import com.yunhefc.stats.application.StatsQueryService;
import com.yunhefc.stats.application.StatsRollupApplicationService;
import com.yunhefc.stats.infrastructure.StatsRollupJob;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * 统计汇总：口径、幂等、失败保留上次结果、Redis 缓存与 STA-01 读取。
 */
class StatsRollupIT extends IntegrationTestSupport {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private StatsRollupApplicationService rollupService;

    @Autowired
    private StatsQueryService statsQueryService;

    @Autowired
    private ApplicationContext context;

    @MockitoSpyBean
    private AttendanceReadPort attendanceReadPort;

    @Test
    void rollsUpCurrentSeasonAttendanceAndEventsAndIsIdempotent() throws Exception {
        String season = currentSeason();
        Long first = insertPlayer("13900006601", "汇总球员一");
        Long second = insertPlayer("13900006602", "汇总球员二");
        Long third = insertPlayer("13900006603", "汇总球员三");
        Long fourth = insertPlayer("13900006604", "汇总球员四");

        Instant start = Instant.now().minus(Duration.ofHours(3));
        Long counted = insertMatch("stats汇总本季", season, 4, start);
        Long cancelled = insertMatch("stats汇总取消", season, 6, start);
        Long previous = insertMatch("stats汇总上季", previousSeason(), 4, start);
        Long fallback = insertMatch("stats汇总空赛季", "", 4, start);

        attendance(counted, first, 0);
        attendance(counted, second, 2);
        attendance(counted, third, 1);
        event(counted, 0, first, second);
        event(counted, 1, first, null);
        event(counted, 0, third, null);

        attendance(cancelled, first, 0);
        event(cancelled, 0, first, null);

        attendance(previous, first, 0);
        event(previous, 0, first, null);

        attendance(fallback, first, 0);
        event(fallback, 0, first, null);

        runJob();
        runJob();

        assertStats(first, season, 2, 2, 0);
        assertStats(second, season, 1, 0, 1);
        assertStats(third, season, 0, 1, 0);
        assertThat(statsRows(season, fourth)).isZero();
        assertThat(statsRowCount(season)).isEqualTo(3);
        assertThat(statsRowCount(previousSeason())).isZero();
    }

    @Test
    void keepsPreviousSummaryWhenRollupFails() throws Exception {
        String season = currentSeason();
        Long player = insertPlayer("13900006611", "汇总失败保留");
        insertStats(player, season, 9, 8, 7);
        insertMatch("stats汇总失败", season, 4, Instant.now().minus(Duration.ofHours(3)));

        doThrow(new IllegalStateException("attendance read failed"))
                .when(attendanceReadPort).findByMatchIds(any());

        runJob();

        assertStats(player, season, 9, 8, 7);
    }

    @Test
    void refreshesCacheAndTeamStatsAndGroupingReadShareTheSummary() throws Exception {
        String season = currentSeason();
        Long player = insertPlayer("13900006621", "汇总缓存球员");
        Instant start = Instant.now().minus(Duration.ofHours(3));
        Long match = insertMatch("stats汇总缓存", season, 4, start);
        attendance(match, player, 0);
        event(match, 0, player, null);

        runJob();


        assertThat(redisTemplate.keys("yunhefc:stats:*")).isNotEmpty();
        mockMvc.perform(get("/api/v1/c/team/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.season").value(season))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].playerId").value(player))
                .andExpect(jsonPath("$.data.items[0].appearances").value(1))
                .andExpect(jsonPath("$.data.items[0].goals").value(1))
                .andExpect(jsonPath("$.data.items[0].assists").value(0))
                .andExpect(jsonPath("$.data.items[0].updatedAt").isNotEmpty());

        Map<Long, PlayerStatsRow> grouped = statsQueryService.seasonStats(1L, season);
        assertThat(grouped.get(player).appearances()).isEqualTo(1);
        assertThat(grouped.get(player).goals()).isEqualTo(1);

        // 汇总表被直接改写后，STA-01 仍命中缓存（允许最多 10 分钟延迟）。
        jdbc.update("""
                UPDATE player_stats_summary SET appearances = 0, goals = 0, assists = 0
                WHERE team_id = 1 AND player_id = ? AND season = ?
                """, player, season);
        mockMvc.perform(get("/api/v1/c/team/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].appearances").value(1));
    }

    @Test
    void quartzJobIsRegisteredEveryTenMinutes() {
        JobDetail detail = context.getBean("statsRollupJobDetail", JobDetail.class);
        assertThat(detail.getJobClass()).isEqualTo(StatsRollupJob.class);
        assertThat(detail.getKey()).isEqualTo(JobKey.jobKey("statsRollupJob", "yunhefc"));

        Trigger trigger = context.getBean("statsRollupTrigger", Trigger.class);
        assertThat(trigger).isInstanceOf(CronTrigger.class);
        assertThat(((CronTrigger) trigger).getCronExpression()).isEqualTo("0 */10 * * * ?");
        assertThat(trigger.getKey())
                .isEqualTo(TriggerKey.triggerKey("statsRollupTrigger", "yunhefc"));
        assertThat(trigger.getJobKey()).isEqualTo(detail.getKey());
    }

    private void runJob() throws Exception {
        StatsRollupJob job = new StatsRollupJob();
        job.setRollupService(rollupService);
        JobExecutionContext context = mock(JobExecutionContext.class);
        Scheduler scheduler = mock(Scheduler.class);
        when(context.getScheduler()).thenReturn(scheduler);
        when(scheduler.getContext()).thenReturn(new SchedulerContext());
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
        job.execute(context);
    }

    private void assertStats(Long playerId, String season, int appearances, int goals, int assists) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT appearances, goals, assists FROM player_stats_summary
                WHERE team_id = 1 AND player_id = ? AND season = ?
                """, playerId, season);
        assertThat(((Number) row.get("appearances")).intValue()).isEqualTo(appearances);
        assertThat(((Number) row.get("goals")).intValue()).isEqualTo(goals);
        assertThat(((Number) row.get("assists")).intValue()).isEqualTo(assists);
    }

    private int statsRows(String season, Long playerId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM player_stats_summary
                WHERE team_id = 1 AND player_id = ? AND season = ?
                """, Integer.class, playerId, season);
    }

    private int statsRowCount(String season) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM player_stats_summary WHERE team_id = 1 AND season = ?
                """, Integer.class, season);
    }

    private void insertStats(Long playerId, String season, int appearances, int goals, int assists) {
        jdbc.update("""
                INSERT INTO player_stats_summary (team_id, player_id, season, appearances, goals, assists)
                VALUES (1, ?, ?, ?, ?, ?)
                """, playerId, season, appearances, goals, assists);
    }

    private static String currentSeason() {
        return String.valueOf(LocalDate.now(SHANGHAI).getYear());
    }

    private static String previousSeason() {
        return String.valueOf(LocalDate.now(SHANGHAI).getYear() - 1);
    }

    private Long insertPlayer(String phone, String name) {
        Long role = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = 'PLAYER'", Long.class);
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, role);
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, 1, 2, '[]', 3)
                """, userId, name);
        return jdbc.queryForObject(
                "SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private Long insertMatch(String title, String season, int status, Instant startAt) {
        Instant endAt = startAt.plus(Duration.ofHours(2));
        Instant signupDeadline = startAt.atZone(SHANGHAI).toLocalDate()
                .atStartOfDay(SHANGHAI).toInstant();
        Instant cancelLock = signupDeadline.plus(Duration.ofHours(10));
        long seasonNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(season_match_no), 0) + 1
                FROM team_match WHERE team_id = 1 AND season = ?
                """, Long.class, season);
        long totalNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(total_match_no), 0) + 1 FROM team_match WHERE team_id = 1
                """, Long.class);
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status,
                    manual_status, capacity, season_match_no, total_match_no, created_by)
                VALUES (1, ?, 1, ?, ?, ?, ?, '测试场地', ?, '橙队', '兰队', ?, 1, 30, ?, ?, 1)
                """, title, Timestamp.from(startAt), Timestamp.from(endAt),
                Timestamp.from(signupDeadline), Timestamp.from(cancelLock), season, status,
                seasonNo, totalNo);
        return jdbc.queryForObject("SELECT id FROM team_match WHERE title = ?", Long.class, title);
    }

    private void attendance(Long matchId, Long playerId, int status) {
        jdbc.update("""
                INSERT INTO attendance (match_id, player_id, status, source, recorded_at)
                VALUES (?, ?, ?, 1, NOW(3))
                """, matchId, playerId, status);
    }

    private void event(Long matchId, int type, Long playerId, Long assistPlayerId) {
        jdbc.update("""
                INSERT INTO match_event (match_id, team, type, player_id, assist_player_id,
                    minute, half, penalty, created_by)
                VALUES (?, 0, ?, ?, ?, 10, 0, 0, 1)
                """, matchId, type, playerId, assistPlayerId);
    }
}
