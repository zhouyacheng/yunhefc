package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.yunhefc.common.util.Ulid;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.infra.outbox.OutboxMessage;
import com.yunhefc.notification.application.NotificationEventConsumer;
import com.yunhefc.notification.domain.NotificationType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

class ContentReportIT extends ActivityITSupport {

    private static final String PASSWORD = "Passw0rd!";
    private static final String NOTIFICATION_TOPIC = "yunhefc.notification.events";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationEventConsumer notificationEventConsumer;

    @Test
    void reportPostNotifiesEveryActiveModeratorOnce() throws Exception {
        Cookie reporter = player("13900006101", "举报球员");
        Cookie admin = admin("13900006102");
        Cookie secondAdmin = admin("13900006103");
        Long disabledAdmin = insertUser("13900006104", "ADMIN");
        jdbc.update("UPDATE app_user SET status = 2 WHERE id = ?", disabledAdmin);
        Long postId = createPost(reporter, "被举报动态");

        report(reporter, """
                {"targetType":0,"targetId":%d,"reason":"  含攻击性内容  "}
                """.formatted(postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reported").value(true));

        assertThat(contentReportTableCount()).isZero();
        Map<String, Object> event = latestEvent(postId);
        assertThat(event.get("aggregate_type")).isEqualTo("CONTENT_POST");
        String payload = (String) event.get("payload_json");
        assertThat(JsonPath.<Integer>read(payload, "$.targetType")).isZero();
        assertThat(JsonPath.<Number>read(payload, "$.targetId").longValue()).isEqualTo(postId);
        assertThat(JsonPath.<Number>read(payload, "$.refId").longValue()).isEqualTo(postId);
        assertThat(JsonPath.<String>read(payload, "$.reason")).isEqualTo("含攻击性内容");

        consume(event, 401L);
        consume(event, 401L);

        assertThat(notificationCount(userId("13900006102"), NotificationType.CONTENT_REPORTED))
                .isEqualTo(1);
        assertThat(notificationCount(userId("13900006103"), NotificationType.CONTENT_REPORTED))
                .isEqualTo(1);
        assertThat(notificationCount(disabledAdmin, NotificationType.CONTENT_REPORTED)).isZero();
        assertThat(notificationCount(userId("13900006101"), NotificationType.CONTENT_REPORTED)).isZero();

        Map<String, Object> notification = latestNotification(userId("13900006102"));
        assertThat(notification.get("title")).isEqualTo("内容举报");
        assertThat((String) notification.get("body")).contains("动态", "含攻击性内容");
        assertThat(notification.get("ref_type")).isEqualTo("CONTENT_POST");
        assertThat(((Number) notification.get("ref_id")).longValue()).isEqualTo(postId);
    }

    @Test
    void reportPhotoUsesPhotoRefTypeAndRejectsDeletedTargets() throws Exception {
        Cookie reporter = player("13900006111", "照片举报球员");
        Cookie admin = admin("13900006112");
        Long userId = userId("13900006111");
        Long albumId = insertAlbum(1, "被举报相册", userId);
        Long photoId = insertPhoto(albumId, userId);
        Long deletedAlbumId = insertAlbum(1, "已删除相册", userId);
        jdbc.update("UPDATE content_album SET is_deleted = 1 WHERE id = ?", deletedAlbumId);
        Long photoInDeletedAlbum = insertPhoto(deletedAlbumId, userId);

        report(reporter, """
                {"targetType":1,"targetId":%d}
                """.formatted(photoId))
                .andExpect(status().isOk());

        Map<String, Object> event = latestEvent(photoId);
        assertThat(event.get("aggregate_type")).isEqualTo("CONTENT_PHOTO");
        assertThat(JsonPath.<Number>read((String) event.get("payload_json"), "$.refId").longValue())
                .isEqualTo(albumId);
        consume(event, 411L);

        Map<String, Object> notification = latestNotification(userId("13900006112"));
        assertThat(notification.get("type")).isEqualTo(NotificationType.CONTENT_REPORTED.code());
        assertThat(notification.get("title")).isEqualTo("内容举报");
        assertThat((String) notification.get("body")).contains("照片");
        assertThat((String) notification.get("body")).doesNotContain("：");
        assertThat(notification.get("ref_type")).isEqualTo("CONTENT_PHOTO");
        // 通知点击进入照片所属相册。
        assertThat(((Number) notification.get("ref_id")).longValue()).isEqualTo(albumId);

        report(reporter, """
                {"targetType":1,"targetId":%d}
                """.formatted(photoInDeletedAlbum))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        report(reporter, """
                {"targetType":1,"targetId":999999}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        assertThat(notificationCount(userId("13900006112"), NotificationType.CONTENT_REPORTED))
                .isEqualTo(1);
    }

    @Test
    void reportValidatesTargetAndVisibility() throws Exception {
        Cookie reporter = player("13900006121", "举报校验球员");
        Long userId = userId("13900006121");
        Long postId = createPost(reporter, "待删除动态");
        jdbc.update("UPDATE content_post SET is_deleted = 1 WHERE id = ?", postId);
        jdbc.update("""
                INSERT INTO content_post (team_id, author_user_id, content)
                VALUES (2, ?, '跨队动态')
                """, userId);
        Long otherTeamPostId = jdbc.queryForObject(
                "SELECT id FROM content_post WHERE content = '跨队动态'", Long.class);

        report(reporter, """
                {"targetType":0,"targetId":%d}
                """.formatted(postId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        report(reporter, """
                {"targetType":0,"targetId":%d}
                """.formatted(otherTeamPostId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        report(reporter, """
                {"targetType":0,"targetId":999999}
                """)
                .andExpect(status().isNotFound());

        report(reporter, """
                {"targetType":2,"targetId":1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        report(reporter, """
                {"targetId":1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        report(reporter, """
                {"targetType":0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        report(reporter, """
                {"targetType":0,"targetId":0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        report(reporter, """
                {"targetType":0,"targetId":1,"reason":"%s"}
                """.formatted("字".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void reportRequiresVisibleLogin() throws Exception {
        Cookie guest = guest("13900006131");
        Cookie pending = pending("13900006132");
        Cookie player = player("13900006133", "可见举报球员");
        Long postId = createPost(player, "正常举报动态");

        mockMvc.perform(post("/api/v1/c/content-reports")
                        .with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":0,\"targetId\":%d}".formatted(postId)))
                .andExpect(status().isUnauthorized());

        report(guest, """
                {"targetType":0,"targetId":%d}
                """.formatted(postId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        report(pending, """
                {"targetType":0,"targetId":%d}
                """.formatted(postId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));

        report(player, """
                {"targetType":0,"targetId":%d}
                """.formatted(postId))
                .andExpect(status().isOk());
    }

    private Cookie player(String phone, String name) throws Exception {
        createPlayerUser(phone, name, Integer.parseInt(phone.substring(phone.length() - 3)), 1);
        return cLogin(phone, PASSWORD);
    }

    private Cookie admin(String phone) throws Exception {
        insertUser(phone, "ADMIN");
        return cLogin(phone, PASSWORD);
    }

    private Cookie guest(String phone) throws Exception {
        Long userId = insertUser(phone, "GUEST");
        insertPlayer(userId, "外挂球员", 31, 0);
        return cLogin(phone, PASSWORD);
    }

    private Cookie pending(String phone) throws Exception {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 0)", phone);
        Long userId = userId(phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)",
                userId, roleId("PLAYER"));
        return cLogin(phone, PASSWORD);
    }

    private Long createPost(Cookie cookie, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/c/posts")
                        .cookie(cookie)
                        .with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"%s\"}".formatted(content)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.data.id").longValue();
    }

    private ResultActions report(Cookie cookie, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/c/content-reports")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", idemKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Long insertAlbum(long teamId, String title, Long userId) {
        jdbc.update("INSERT INTO content_album (team_id, title, created_by) VALUES (?, ?, ?)",
                teamId, title, userId);
        return jdbc.queryForObject("SELECT id FROM content_album WHERE title = ?", Long.class, title);
    }

    private Long insertPhoto(Long albumId, Long userId) {
        String objectKey = "content/" + userId + "/" + Ulid.generate() + ".jpg";
        jdbc.update("INSERT INTO content_album_photo (album_id, object_key, uploaded_by) VALUES (?, ?, ?)",
                albumId, objectKey, userId);
        return jdbc.queryForObject(
                "SELECT id FROM content_album_photo WHERE object_key = ?", Long.class, objectKey);
    }

    private int contentReportTableCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name LIKE 'content_report%'
                """, Integer.class);
    }

    private Map<String, Object> latestEvent(Long targetId) {
        return jdbc.queryForMap("""
                SELECT event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json
                FROM outbox_event
                WHERE event_type = ? AND aggregate_id = ? AND is_deleted = 0
                ORDER BY id DESC LIMIT 1
                """, EventTypes.CONTENT_REPORTED, targetId);
    }

    private void consume(Map<String, Object> row, long offset) throws Exception {
        OutboxMessage message = new OutboxMessage(
                (String) row.get("event_id"),
                row.get("team_id") == null ? null : ((Number) row.get("team_id")).longValue(),
                (String) row.get("aggregate_type"),
                ((Number) row.get("aggregate_id")).longValue(),
                (String) row.get("event_type"),
                (String) row.get("payload_json"));
        notificationEventConsumer.apply(objectMapper.writeValueAsString(message),
                NOTIFICATION_TOPIC, 0, offset);
    }

    private int notificationCount(Long userId, NotificationType type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = ?",
                Integer.class, userId, type.code());
    }

    private Map<String, Object> latestNotification(Long userId) {
        return jdbc.queryForMap("""
                SELECT user_id, type, title, body, ref_type, ref_id
                FROM notification WHERE user_id = ? ORDER BY id DESC LIMIT 1
                """, userId);
    }
}
