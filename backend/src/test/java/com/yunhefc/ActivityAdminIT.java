package com.yunhefc;

import static com.yunhefc.AvatarFixtures.JPEG;
import static com.yunhefc.AvatarFixtures.PNG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yunhefc.infra.storage.InMemoryOssGateway;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

class ActivityAdminIT extends ActivityITSupport {

    private static final Pattern IMAGE_KEY =
            Pattern.compile("^activity/\\d+/[0-9A-HJKMNP-TV-Z]{26}\\.(jpg|png|webp)$");

    @Autowired
    private InMemoryOssGateway ossGateway;

    @Test
    void createsActivityAndWritesAuditAndNotificationEvent() throws Exception {
        Cookie admin = bAdminLogin("13900002001");

        MvcResult result = createActivity(admin, "冬季聚餐", futureAt(30, 18), 4)
                .andExpect(jsonPath("$.data.title").value("冬季聚餐"))
                .andExpect(jsonPath("$.data.type").value(0))
                .andExpect(jsonPath("$.data.typeLabel").value("DINNER"))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.statusLabel").value("SIGNUP_OPEN"))
                .andExpect(jsonPath("$.data.signupCount").value(0))
                .andExpect(jsonPath("$.data.signupOpen").value(true))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.createdBy").isNumber())
                .andExpect(jsonPath("$.data.familyAllowed").value(true))
                .andExpect(jsonPath("$.data.locationImageUrls").isEmpty())
                .andExpect(jsonPath("$.data.costPerPerson").value(88.50))
                .andReturn();
        Long activityId = activityId(result);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'ACTIVITY_CREATE'
                """, Integer.class, activityId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'ACTIVITY_CREATED' AND aggregate_id = ?
                """, Integer.class, activityId)).isEqualTo(1);
        String payload = jdbc.queryForObject("""
                SELECT payload_json FROM outbox_event
                WHERE event_type = 'ACTIVITY_CREATED' AND aggregate_id = ?
                """, String.class, activityId);
        assertThat(payload).contains("冬季聚餐", "队内餐厅").doesNotContain("13900002001");
    }

    @Test
    void adminViewReturnsLocationImageKeysForCopyAndEdit() throws Exception {
        Cookie admin = bAdminLogin("13900002005");
        String firstKey = "activity/12/01JABCDEFGHJKMNPQRSTVWXYZ0.png";
        String secondKey = "activity/12/01JABCDEFGHJKMNPQRSTVWXYZ1.jpg";

        MvcResult created = createActivity(admin, """
                {"title":"带图活动","type":2,"startAt":"%s","signupDeadline":"%s",
                 "familyAllowed":true,"location":"体育馆",
                 "locationImageKeys":["%s","%s"]}
                """.formatted(futureAt(30, 18), futureAt(30, 17), firstKey, secondKey))
                .andExpect(jsonPath("$.data.locationImageKeys[0]").value(firstKey))
                .andExpect(jsonPath("$.data.locationImageKeys[1]").value(secondKey))
                .andExpect(jsonPath("$.data.locationImageUrls[0]")
                        .value("mock://oss/" + firstKey))
                .andReturn();
        Long activityId = activityId(created);

        mockMvc.perform(get("/api/v1/b/activities/" + activityId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationImageKeys[1]").value(secondKey))
                .andExpect(jsonPath("$.data.version").value(0));

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locationImageKeys":[],"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locationImageKeys").isEmpty())
                .andExpect(jsonPath("$.data.locationImageUrls").isEmpty());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM activity_image WHERE activity_id = ? AND is_deleted = 1
                """, Integer.class, activityId)).isEqualTo(2);

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locationImageKeys":null,"version":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.locationImageKeys").exists());
    }

    @Test
    void adminListFiltersByStartDateRange() throws Exception {
        Cookie admin = bAdminLogin("13900002006");
        String inRange = futureAt(30, 18);
        String outOfRange = futureAt(40, 18);
        createActivity(admin, "范围内活动", inRange, 4);
        createActivity(admin, "范围外活动", outOfRange, 4);
        String day = inRange.substring(0, 10);

        mockMvc.perform(get("/api/v1/b/activities").cookie(admin)
                        .param("startDate", day)
                        .param("endDate", day))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("范围内活动"));

        mockMvc.perform(get("/api/v1/b/activities").cookie(admin)
                        .param("startDate", day))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/b/activities").cookie(admin)
                        .param("startDate", "2026-13-01")
                        .param("endDate", "2026-13-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/b/activities").cookie(admin)
                        .param("startDate", day)
                        .param("endDate", java.time.LocalDate.parse(day).minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void adminListRosterAndRemoveExposeContract() throws Exception {
        Cookie admin = bAdminLogin("13900002091");
        Long activityId = activityId(createActivity(admin, "管理名单活动", futureAt(30, 18), 3));

        createPlayerUser("13900002092", "名单甲", 891, 1);
        createPlayerUser("13900002093", "名单乙", 892, 1);
        Long firstPlayer = playerId("13900002092");
        Long secondPlayer = playerId("13900002093");
        jdbc.update("""
                INSERT INTO activity_signup (activity_id, player_id, with_family, family_note,
                                             status, signed_up_at)
                VALUES (?, ?, 1, '两位家属', 0, ?)
                """, activityId, firstPlayer, pastAt(1, 10).replace('T', ' '));
        jdbc.update("""
                INSERT INTO activity_signup (activity_id, player_id, status, signed_up_at)
                VALUES (?, ?, 0, ?)
                """, activityId, secondPlayer, pastAt(1, 11).replace('T', ' '));
        Long secondSignup = jdbc.queryForObject("""
                SELECT id FROM activity_signup WHERE activity_id = ? AND player_id = ?
                """, Long.class, activityId, secondPlayer);

        mockMvc.perform(get("/api/v1/b/activities").cookie(admin).param("keyword", "管理名单"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value(0))
                .andExpect(jsonPath("$.data.items[0].typeLabel").value("DINNER"))
                .andExpect(jsonPath("$.data.items[0].status").value(0))
                .andExpect(jsonPath("$.data.items[0].statusLabel").value("SIGNUP_OPEN"))
                .andExpect(jsonPath("$.data.items[0].signupCount").value(2))
                .andExpect(jsonPath("$.data.items[0].signupOpen").value(true))
                .andExpect(jsonPath("$.data.items[0].version").value(0));

        mockMvc.perform(get("/api/v1/b/activities/" + activityId + "/signups").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].playerName").value("名单甲"))
                .andExpect(jsonPath("$.data.items[0].playerNo").value(891))
                .andExpect(jsonPath("$.data.items[0].withFamily").value(true))
                .andExpect(jsonPath("$.data.items[0].familyNote").value("两位家属"))
                .andExpect(jsonPath("$.data.items[0].status").value(0))
                .andExpect(jsonPath("$.data.items[0].statusLabel").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.items[0].signedUpAt").exists())
                .andExpect(jsonPath("$.data.items[1].playerName").value("名单乙"));

        mockMvc.perform(get("/api/v1/b/activities/" + activityId + "/signups")
                        .cookie(admin).param("pageSize", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        String removeKey = idemKey();
        mockMvc.perform(delete("/api/v1/b/activities/" + activityId + "/signups/" + secondSignup)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", removeKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(true));
        mockMvc.perform(delete("/api/v1/b/activities/" + activityId + "/signups/" + secondSignup)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", removeKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(true));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'ACTIVITY_SIGNUP_REMOVE'
                """, Integer.class, secondSignup)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/b/activities/" + activityId + "/signups").cookie(admin))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void createRejectsInvalidCrossFields() throws Exception {
        Cookie admin = bAdminLogin("13900002011");
        String template = """
                {"title":"非法活动","type":0,"startAt":"2030-12-20T18:00:00","endAt":"%s",
                 "signupDeadline":"%s","capacity":%d,"familyAllowed":true,"costPerPerson":%s,
                 "location":"餐厅","locationImageKeys":%s}
                """;

        mockMvc.perform(post("/api/v1/b/activities").cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("2030-12-20T18:00:00", "2030-12-20T17:00:00",
                                4, "88.50", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.endAt").exists());

        mockMvc.perform(post("/api/v1/b/activities").cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("2030-12-20T20:00:00", "2030-12-20T18:30:00",
                                4, "88.50", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.details.signupDeadline").exists());

        mockMvc.perform(post("/api/v1/b/activities").cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("2030-12-20T20:00:00", "2030-12-20T17:00:00",
                                0, "88.50", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.details.capacity").exists());

        mockMvc.perform(post("/api/v1/b/activities").cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("2030-12-20T20:00:00", "2030-12-20T17:00:00",
                                4, "88.555", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.details.costPerPerson").exists());

        mockMvc.perform(post("/api/v1/b/activities").cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("2030-12-20T20:00:00", "2030-12-20T17:00:00",
                                4, "88.50", "[\"avatar/1/not-activity.jpg\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.details.locationImageKeys").exists());

        mockMvc.perform(post("/api/v1/b/activities").cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("2030-12-20T20:00:00", "2030-12-20T17:00:00",
                                4, "88.50", "[\"activity/1/short.jpg\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.details.locationImageKeys").exists());

        String sameKey = "activity/1/01JABCDEFGHJKMNPQRSTVWXYZ0.png";
        mockMvc.perform(post("/api/v1/b/activities").cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("2030-12-20T20:00:00", "2030-12-20T17:00:00",
                                4, "88.50", "[\"%s\",\"%s\"]".formatted(sameKey, sameKey))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.details.locationImageKeys")
                        .value("场地环境图片不能重复"));

        String eleven = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> "\"activity/1/01JABCDEFGHJKMNPQRSTVWXYZ" + "0123456789A".charAt(i)
                        + ".png\"")
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/api/v1/b/activities").cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted("2030-12-20T20:00:00", "2030-12-20T17:00:00",
                                4, "88.50", "[" + eleven + "]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.details.locationImageKeys").exists());
    }

    @Test
    void updatesOnlyEditableActivitiesAndDetectsVersionConflict() throws Exception {
        Cookie admin = bAdminLogin("13900002021");
        Long activityId = activityId(createActivity(admin, "可编辑活动", futureAt(30, 18), 4));

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  改名活动  ","endAt":null,"capacity":null,
                                 "costPerPerson":null,"description":null,"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("改名活动"))
                .andExpect(jsonPath("$.data.endAt").value((Object) null))
                .andExpect(jsonPath("$.data.capacity").value((Object) null))
                .andExpect(jsonPath("$.data.costPerPerson").value((Object) null))
                .andExpect(jsonPath("$.data.description").value((Object) null))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"过期版本\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(1));

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2,\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.details.status").exists());

        changeStatus(admin, activityId, 2, 1, null);
        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"终态改名\",\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40917));
    }

    @Test
    void enforcesActivityStatusMachine() throws Exception {
        Cookie admin = bAdminLogin("13900002031");
        Long activityId = activityId(createActivity(admin, "状态机活动", futureAt(30, 18), 4));

        changeStatus(admin, activityId, 1, 0, null);
        // 报名截止可恢复为报名中（P-111）。
        changeStatus(admin, activityId, 0, 1, null);
        changeStatus(admin, activityId, 1, 2, null);
        changeStatus(admin, activityId, 2, 3, null);
        // 已结束不可取消，但可重新开放为报名中/报名截止（P-110）。
        changeStatus(admin, activityId, 3, 4, "结束不可取消", status().isConflict(), 40917);
        changeStatus(admin, activityId, 0, 4, null);
        changeStatus(admin, activityId, 2, 5, null);
        changeStatus(admin, activityId, 1, 6, null);

        Long cancelId = activityId(createActivity(admin, "取消活动", futureAt(31, 18), 4));
        changeStatus(admin, cancelId, 3, 0, "  场地停业  ");
        assertThat(jdbc.queryForObject(
                "SELECT cancel_reason FROM team_activity WHERE id = ?", String.class, cancelId))
                .isEqualTo("场地停业");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'ACTIVITY_CANCELLED' AND aggregate_id = ?
                """, Integer.class, cancelId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'ACTIVITY_STATUS_CHANGE'
                """, Integer.class, cancelId)).isEqualTo(1);

        Long noReasonId = activityId(createActivity(admin, "缺原因活动", futureAt(32, 18), 4));
        changeStatus(admin, noReasonId, 3, 0, "   ", status().isBadRequest(), 40001);

        Long directFinishId = activityId(createActivity(admin, "直接结束", futureAt(33, 18), 4));
        changeStatus(admin, directFinishId, 2, 0, null);
    }

    @Test
    void removeSignupCancelsRowAndResignupReusesIt() throws Exception {
        Cookie admin = bAdminLogin("13900002041");
        Long activityId = activityId(createActivity(admin, "移除报名活动", futureAt(30, 18), 4));
        createPlayerUser("13900002042", "活动报名者", 801, 1);
        Long playerId = playerId("13900002042");
        Cookie player = cLogin("13900002042", "Passw0rd!");

        MvcResult signup = mockMvc.perform(post("/api/v1/c/activities/" + activityId + "/signups")
                        .cookie(player).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withFamily\":true,\"familyNote\":\"带一位家属\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signup.status").value(0))
                .andReturn();
        Long signupId = JsonPath.<Number>read(
                signup.getResponse().getContentAsString(), "$.data.signup.id").longValue();

        mockMvc.perform(delete("/api/v1/b/activities/" + activityId + "/signups/" + signupId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(true));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM activity_signup WHERE id = ?", Integer.class, signupId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT is_deleted FROM activity_signup WHERE id = ?", Integer.class, signupId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT cancelled_by FROM activity_signup WHERE id = ?", Long.class, signupId)).isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'ACTIVITY_SIGNUP_REMOVE'
                """, Integer.class, signupId)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/b/activities/" + activityId + "/signups/" + signupId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(false));

        MvcResult resignup = mockMvc.perform(post("/api/v1/c/activities/" + activityId + "/signups")
                        .cookie(player).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withFamily\":false}"))
                .andExpect(status().isOk())
                .andReturn();
        Long resignupId = JsonPath.<Number>read(
                resignup.getResponse().getContentAsString(), "$.data.signup.id").longValue();
        assertThat(resignupId).isEqualTo(signupId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_signup WHERE activity_id = ? AND player_id = ?",
                Integer.class, activityId, playerId)).isEqualTo(1);
    }

    @Test
    void adminEndpointsRequireManagePermissionAndHideOtherTeams() throws Exception {
        Cookie playerBackend = bLogin("13900002051", "PLAYER");
        mockMvc.perform(get("/api/v1/b/activities").cookie(playerBackend))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(post("/api/v1/b/activities").cookie(playerBackend).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权\",\"type\":0,\"startAt\":\"2030-12-20T18:00:00\","
                                + "\"signupDeadline\":\"2030-12-20T17:00:00\",\"familyAllowed\":false,"
                                + "\"location\":\"餐厅\"}"))
                .andExpect(status().isForbidden());

        jdbc.update("""
                INSERT INTO team_activity (team_id, title, type, start_at, signup_deadline, location,
                                           status, version, created_by)
                VALUES (2, '他队活动', 0, '2030-12-20 18:00:00', '2030-12-20 17:00:00',
                        '他队餐厅', 0, 0, 1)
                """);
        Long otherTeamId = jdbc.queryForObject(
                "SELECT id FROM team_activity WHERE title = '他队活动'", Long.class);

        Cookie admin = bAdminLogin("13900002052");
        mockMvc.perform(get("/api/v1/b/activities/" + otherTeamId).cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(get("/api/v1/c/activities/" + otherTeamId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void createReplayReturnsFirstResult() throws Exception {
        Cookie admin = bAdminLogin("13900002061");
        String body = """
                {"title":"幂等活动","type":1,"startAt":"2030-12-20T18:00:00",
                 "signupDeadline":"2030-12-20T17:00:00","familyAllowed":true,"location":"年会厅"}
                """;
        String key = idemKey();
        MvcResult first = mockMvc.perform(post("/api/v1/b/activities").cookie(admin).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/b/activities").cookie(admin).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(activityId(second)).isEqualTo(activityId(first));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_activity WHERE title = '幂等活动'", Integer.class)).isEqualTo(1);
    }

    @Test
    void uploadsActivityImageWithinLimitsAndRejectsUnsupportedFiles() throws Exception {
        Cookie admin = bAdminLogin("13900002071");
        Long adminUserId = userId("13900002071");

        MvcResult result = mockMvc.perform(multipart("/api/v1/b/files/activity-image")
                        .file(new MockMultipartFile("file", "venue.png", "image/png", PNG))
                        .cookie(admin).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String objectKey = JsonPath.read(result.getResponse().getContentAsString(), "$.data.objectKey");
        String signedUrl = JsonPath.read(result.getResponse().getContentAsString(), "$.data.signedUrl");
        assertThat(objectKey).matches(IMAGE_KEY).startsWith("activity/" + adminUserId + "/");
        assertThat(signedUrl).isEqualTo("mock://oss/" + objectKey);
        assertThat(ossGateway.getObject(objectKey)).isEqualTo(PNG);

        byte[] large = jpegOfSize((int) (4.9 * 1024 * 1024));
        mockMvc.perform(multipart("/api/v1/b/files/activity-image")
                        .file(new MockMultipartFile("file", "large.jpg", "image/jpeg", large))
                        .cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        byte[] oversized = jpegOfSize((int) (5.1 * 1024 * 1024));
        mockMvc.perform(multipart("/api/v1/b/files/activity-image")
                        .file(new MockMultipartFile("file", "too-large.jpg", "image/jpeg", oversized))
                        .cookie(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.file").value("图片不能超过 5MB"));

        mockMvc.perform(multipart("/api/v1/b/files/activity-image")
                        .file(new MockMultipartFile("file", "fake.png", "image/png",
                                "%PDF-1.7 not an image".getBytes(StandardCharsets.US_ASCII)))
                        .cookie(admin).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41501));

        mockMvc.perform(multipart("/api/v1/b/files/activity-image")
                        .file(new MockMultipartFile("file", "a.gif", "image/gif",
                                "GIF89a".getBytes(StandardCharsets.US_ASCII)))
                        .cookie(admin).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41501));

        Cookie playerBackend = bLogin("13900002072", "PLAYER");
        mockMvc.perform(multipart("/api/v1/b/files/activity-image")
                        .file(new MockMultipartFile("file", "venue.png", "image/png", PNG))
                        .cookie(playerBackend).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRejectsCapacityBelowActiveSignups() throws Exception {
        Cookie admin = bAdminLogin("13900002081");
        Long activityId = activityId(createActivity(admin, "容量活动", futureAt(30, 18), 4));
        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                    INSERT INTO activity_signup (activity_id, player_id, status)
                    VALUES (?, ?, 0)
                    """, activityId, 900 + i);
        }

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":2,\"version\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":3,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity").value(3))
                .andExpect(jsonPath("$.data.signupCount").value(3))
                .andExpect(jsonPath("$.data.signupOpen").value(false));
    }

    @Test
    void updateRejectsClientSuppliedPresenceFields() throws Exception {
        Cookie admin = bAdminLogin("13900002085");
        Long activityId = activityId(createActivity(admin, "指纹活动", futureAt(30, 18), 4));

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"presentFields\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.presentFields").exists());

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"status\":2,\"unknownFields\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.status").exists())
                .andExpect(jsonPath("$.data.details.unknownFields").exists());
    }

    @Test
    void updateIdempotencyHashDistinguishesExplicitNull() throws Exception {
        Cookie admin = bAdminLogin("13900002082");
        Long activityId = activityId(createActivity(admin, "哈希活动", futureAt(30, 18), 4));
        String key = idemKey();

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(patch("/api/v1/b/activities/" + activityId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endAt\":null,\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));
    }

    @Test
    void disablePlayerCancelsOnlyActiveFutureSignups() throws Exception {
        Cookie admin = bAdminLogin("13900002083");
        Long adminUserId = userId("13900002083");
        createPlayerUser("13900002084", "注销活动球员", 840, 1);
        Long playerId = playerId("13900002084");
        Long futureId = insertActivity("注销未来活动", futureAt(30, 18), 0, 10,
                futureAt(30, 17), 1);
        Long finishedId = insertActivity("注销历史活动", pastAt(30, 18), 2, 10,
                pastAt(30, 17), 1);
        jdbc.update("""
                INSERT INTO activity_signup (activity_id, player_id, status)
                VALUES (?, ?, 0), (?, ?, 0)
                """, futureId, playerId, finishedId, playerId);

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/disable")
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"离队\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                SELECT status FROM activity_signup WHERE activity_id = ? AND player_id = ?
                """, Integer.class, futureId, playerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT cancelled_by FROM activity_signup WHERE activity_id = ? AND player_id = ?
                """, Long.class, futureId, playerId)).isEqualTo(adminUserId);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM activity_signup WHERE activity_id = ? AND player_id = ?
                """, Integer.class, finishedId, playerId)).isZero();
    }

    private void changeStatus(Cookie admin, Long activityId, int status, int version, String reason)
            throws Exception {
        changeStatus(admin, activityId, status, version, reason, status().isOk(), 0);
    }

    private void changeStatus(Cookie admin, Long activityId, int status, int version, String reason,
                              org.springframework.test.web.servlet.ResultMatcher expectedStatus,
                              int expectedCode) throws Exception {
        String body = reason == null
                ? "{\"status\":%d,\"version\":%d}".formatted(status, version)
                : "{\"status\":%d,\"version\":%d,\"reason\":\"%s\"}".formatted(status, version, reason);
        var actions = mockMvc.perform(post("/api/v1/b/activities/" + activityId + "/status")
                .cookie(admin).with(csrf())
                .header("Idempotency-Key", idemKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
        if (expectedCode == 0) {
            actions.andExpect(expectedStatus);
        } else {
            actions.andExpect(expectedStatus).andExpect(jsonPath("$.code").value(expectedCode));
        }
    }

    private static byte[] jpegOfSize(int size) {
        byte[] content = new byte[size];
        System.arraycopy(JPEG, 0, content, 0, JPEG.length - 2);
        content[size - 2] = (byte) 0xFF;
        content[size - 1] = (byte) 0xD9;
        return content;
    }
}
