package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import com.yunhefc.common.time.ShanghaiTime;

class SettingsIT extends IntegrationTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void returnsEffectiveDefaultsWhenRowsAreMissingOrUnparseable() throws Exception {
        jdbc.update("DELETE FROM system_setting");
        Cookie admin = adminLogin("13900003001");

        expectDefaults(get("/api/v1/b/settings").cookie(admin));

        jdbc.update("""
                INSERT INTO system_setting (team_id, setting_key, setting_value, value_type)
                VALUES (1, 'registration.maxPlayers', JSON_QUOTE('abc'), 'NUMBER')
                """);

        expectDefaults(get("/api/v1/b/settings").cookie(admin));
    }

    @Test
    void patchMergesSubsetAndPersistsJsonNumbers() throws Exception {
        Cookie admin = adminLogin("13900003002");

        patchSettings(admin, "{\"settings\":{\"maxGuests\":6}}", UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.maxGuests").value(6))
                .andExpect(jsonPath("$.data.settings.maxPlayers").value(30));

        patchSettings(admin, "{\"settings\":{\"maxPlayers\":42}}", UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.maxPlayers").value(42))
                .andExpect(jsonPath("$.data.settings.maxGuests").value(6))
                .andExpect(jsonPath("$.data.settings.maxWaitlist").value(2));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_setting WHERE team_id = 1",
                Integer.class)).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM system_setting
                WHERE team_id = 1 AND setting_key LIKE 'grouping.%' AND is_deleted = 0
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT JSON_TYPE(setting_value)
                FROM system_setting
                WHERE team_id = 1 AND setting_key = 'registration.maxPlayers'
                """, String.class)).isEqualTo("INTEGER");
        assertThat(jdbc.queryForObject("""
                SELECT CAST(setting_value AS SIGNED)
                FROM system_setting
                WHERE team_id = 1 AND setting_key = 'registration.maxPlayers'
                """, Integer.class)).isEqualTo(42);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM system_setting
                WHERE team_id = 1 AND value_type = 'NUMBER' AND is_deleted = 0
                AND setting_key LIKE 'registration.%'
                """, Integer.class)).isEqualTo(3);
    }

    @Test
    void emptyPatchKeepsCurrentValuesAndIsAccepted() throws Exception {
        Cookie admin = adminLogin("13900003003");

        patchSettings(admin, "{\"settings\":{}}", UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.maxPlayers").value(30))
                .andExpect(jsonPath("$.data.settings.maxGuests").value(4));
    }

    @Test
    void rejectsMissingSettingsBody() throws Exception {
        Cookie admin = adminLogin("13900003004");

        mockMvc.perform(patch("/api/v1/b/settings")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rejectsNonIntegerValues() throws Exception {
        Cookie admin = adminLogin("13900003011");
        List<String> invalidBodies = List.of(
                "{\"settings\":{\"maxPlayers\":1.9}}",
                "{\"settings\":{\"maxPlayers\":\"7\"}}",
                "{\"settings\":{\"maxPlayers\":true}}",
                "{\"settings\":{\"maxPlayers\":2147483648}}");

        for (String body : invalidBodies) {
            patchSettings(admin, body, UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }

        assertThat(settingsUpdateAuditCount()).isZero();
    }

    @Test
    void rejectsOutOfRangeValuesWithoutWriting() throws Exception {
        Cookie admin = adminLogin("13900003005");
        List<String> invalidBodies = List.of(
                "{\"settings\":{\"maxPlayers\":0}}",
                "{\"settings\":{\"maxPlayers\":100}}",
                "{\"settings\":{\"maxGuests\":-1}}",
                "{\"settings\":{\"maxGuests\":100}}",
                "{\"settings\":{\"maxWaitlist\":-1}}",
                "{\"settings\":{\"maxWaitlist\":100}}");

        for (String body : invalidBodies) {
            patchSettings(admin, body, UUID.randomUUID().toString())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }

        assertThat(jdbc.queryForObject("""
                SELECT CAST(setting_value AS SIGNED) FROM system_setting
                WHERE team_id = 1 AND setting_key = 'registration.maxPlayers'
                """, Integer.class)).isEqualTo(30);
        assertThat(settingsUpdateAuditCount()).isZero();
    }

    @Test
    void acceptsInclusiveBoundaries() throws Exception {
        Cookie admin = adminLogin("13900003006");

        patchSettings(admin, """
                {"settings":{"maxPlayers":1,"maxGuests":0,"maxWaitlist":0}}
                """, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.maxPlayers").value(1))
                .andExpect(jsonPath("$.data.settings.maxGuests").value(0))
                .andExpect(jsonPath("$.data.settings.maxWaitlist").value(0));

        patchSettings(admin, """
                {"settings":{"maxPlayers":99,"maxGuests":99,"maxWaitlist":99}}
                """, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.maxPlayers").value(99))
                .andExpect(jsonPath("$.data.settings.maxGuests").value(99))
                .andExpect(jsonPath("$.data.settings.maxWaitlist").value(99));
    }

    @Test
    void writesOneAuditWithFullSnapshotsAndIsIdempotent() throws Exception {
        Cookie admin = adminLogin("13900003007");
        Long adminId = userIdOf("13900003007");
        String idempotencyKey = UUID.randomUUID().toString();

        patchSettings(admin, "{\"settings\":{\"maxPlayers\":42}}", idempotencyKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.maxPlayers").value(42));
        patchSettings(admin, "{\"settings\":{\"maxPlayers\":42}}", idempotencyKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settings.maxPlayers").value(42));

        assertThat(settingsUpdateAuditCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT CAST(JSON_EXTRACT(payload_json, '$.actorUserId') AS SIGNED)
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'SETTINGS_UPDATE'
                """, Long.class)).isEqualTo(adminId);
        assertThat(jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.targetType'))
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'SETTINGS_UPDATE'
                """, String.class)).isEqualTo("SETTINGS");
        assertThat(jdbc.queryForObject("""
                SELECT CAST(JSON_EXTRACT(payload_json, '$.targetId') AS SIGNED)
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'SETTINGS_UPDATE'
                """, Long.class)).isEqualTo(1L);
        assertThat(snapshotLength("beforeJson")).isEqualTo(3);
        assertThat(snapshotLength("afterJson")).isEqualTo(3);
        assertThat(snapshotNumber("beforeJson", "maxPlayers")).isEqualTo(30);
        assertThat(snapshotNumber("afterJson", "maxPlayers")).isEqualTo(42);
    }

    @Test
    void enforcesViewAndManagePermissions() throws Exception {
        Cookie viewer = loginWithPermissions("13900003008", "SETTINGS_VIEWER", "settings:view:any");
        mockMvc.perform(get("/api/v1/b/settings").cookie(viewer))
                .andExpect(status().isOk());
        patchSettings(viewer, "{\"settings\":{\"maxPlayers\":41}}", UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        Cookie other = loginWithPermissions("13900003009", "MATCH_VIEWER", "match:view:any");
        mockMvc.perform(get("/api/v1/b/settings").cookie(other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/v1/b/settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void newMatchesUseUpdatedRegistrationDefaults() throws Exception {
        Cookie admin = adminLogin("13900003010");
        patchSettings(admin, """
                {"settings":{"maxPlayers":12,"maxGuests":5,"maxWaitlist":3}}
                """, UUID.randomUUID().toString())
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"配置默认值","type":1,"startAt":"%s","endAt":"%s",
                                 "venue":"聚龙足球场","homeTeamName":"橙队","awayTeamName":"兰队"}
                                """.formatted(local("2026-12-01T20:00:00"), local("2026-12-01T22:00:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity").value(12))
                .andExpect(jsonPath("$.data.guestCapacity").value(5))
                .andExpect(jsonPath("$.data.waitlistCapacity").value(3));
    }

    private void expectDefaults(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.settings.maxPlayers").value(30))
                .andExpect(jsonPath("$.data.settings.maxGuests").value(4))
                .andExpect(jsonPath("$.data.settings.maxWaitlist").value(2));
    }

    private ResultActions patchSettings(Cookie actor, String body, String idempotencyKey) throws Exception {
        return mockMvc.perform(patch("/api/v1/b/settings")
                .cookie(actor)
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int settingsUpdateAuditCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'SETTINGS_UPDATE'
                """, Integer.class);
    }

    private int snapshotLength(String jsonField) {
        return jdbc.queryForObject("""
                SELECT JSON_LENGTH(JSON_EXTRACT(
                    JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.%s')), '$'))
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'SETTINGS_UPDATE'
                """.formatted(jsonField), Integer.class);
    }

    private int snapshotNumber(String jsonField, String key) {
        return jdbc.queryForObject("""
                SELECT CAST(JSON_EXTRACT(
                    JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.%s')), '$.%s') AS SIGNED)
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'SETTINGS_UPDATE'
                """.formatted(jsonField, key), Integer.class);
    }

    private Cookie adminLogin(String phone) throws Exception {
        insertUser(phone, "ADMIN");
        return login(phone);
    }

    private Cookie loginWithPermissions(String phone, String roleCode, String... permissions)
            throws Exception {
        jdbc.update("INSERT INTO app_role (team_id, code, name, is_default) VALUES (1, ?, ?, 0)",
                roleCode, roleCode);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, roleCode);
        for (String permission : permissions) {
            jdbc.update("""
                    INSERT INTO role_permission (role_id, permission_id)
                    SELECT ?, id FROM permission WHERE code = ?
                    """, roleId, permission);
        }
        insertUser(phone, roleCode);
        return login(phone);
    }

    private void insertUser(String phone, String roleCode) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = userIdOf(phone);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, roleCode);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private Long userIdOf(String phone) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Cookie login(String phone) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode("Admin123!"), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, "Admin123!")))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }

    private LocalDateTime local(String localDateTime) {
        return ShanghaiTime.parseLenient(localDateTime);
    }
}
