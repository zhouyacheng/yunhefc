package com.yunhefc;

import static com.yunhefc.AvatarFixtures.JPEG;
import static com.yunhefc.AvatarFixtures.PNG;
import static com.yunhefc.AvatarFixtures.WEBP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import java.util.regex.Pattern;

class AvatarUploadIT extends IntegrationTestSupport {

    private static final Pattern OBJECT_KEY =
            Pattern.compile("^avatar/\\d+/[0-9A-HJKMNP-TV-Z]{26}\\.(jpg|png|webp)$");

    @Autowired
    private InMemoryOssGateway ossGateway;

    @Test
    void activePlayerUploadsAvatarWithoutChangingProfile() throws Exception {
        Long userId = insertActiveUser("13900000701", roleId("PLAYER"));
        Long playerId = insertPlayer(userId, "头像甲", 901);
        Cookie session = cLogin("13900000701", "Passw0rd!");

        MvcResult result = mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "me.jpg", "image/jpeg", JPEG))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String objectKey = JsonPath.read(result.getResponse().getContentAsString(), "$.data.objectKey");
        String signedUrl = JsonPath.read(result.getResponse().getContentAsString(), "$.data.signedUrl");
        assertThat(objectKey).matches(OBJECT_KEY).startsWith("avatar/" + userId + "/").endsWith(".jpg");
        assertThat(signedUrl).isEqualTo("mock://oss/" + objectKey);
        assertThat(ossGateway.getObject(objectKey)).isEqualTo(JPEG);
        assertThat(ossGateway.contentType(objectKey)).isEqualTo("image/jpeg");

        assertThat(jdbc.queryForObject("SELECT avatar_url FROM player WHERE id = ?", String.class, playerId))
                .isNull();
    }

    @Test
    void pendingPlayerCanUploadAvatar() throws Exception {
        Cookie session = registerPending("13900000702", "头像待审核", 2);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, "13900000702");

        MvcResult result = mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "pending.png", "image/png", PNG))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        String objectKey = JsonPath.read(result.getResponse().getContentAsString(), "$.data.objectKey");
        assertThat(objectKey).startsWith("avatar/" + userId + "/").endsWith(".png");
        assertThat(ossGateway.getObject(objectKey)).isEqualTo(PNG);
    }

    @Test
    void detectsFormatFromBytesNotFromClientMetadata() throws Exception {
        Long userId = insertActiveUser("13900000703", roleId("PLAYER"));
        insertPlayer(userId, "头像嗅探", 902);
        Cookie session = cLogin("13900000703", "Passw0rd!");

        MvcResult result = mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "fake.txt", "application/pdf", JPEG))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(JsonPath.read(result.getResponse().getContentAsString(), "$.data.objectKey").toString())
                .endsWith(".jpg");
    }

    @Test
    void adminUploadsAvatarForPlayer() throws Exception {
        Cookie admin = adminLogin("13900000704");
        Long targetUserId = insertActiveUser("13900000705", roleId("PLAYER"));
        Long playerId = insertPlayer(targetUserId, "头像代传", 903);

        MvcResult result = mockMvc.perform(multipart("/api/v1/b/players/" + playerId + "/avatar")
                        .file(new MockMultipartFile("file", "guest.webp", "image/webp", WEBP))
                        .cookie(admin)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        String objectKey = JsonPath.read(result.getResponse().getContentAsString(), "$.data.objectKey");
        assertThat(objectKey).startsWith("avatar/" + targetUserId + "/").endsWith(".webp");
        assertThat(jdbc.queryForObject("SELECT avatar_url FROM player WHERE id = ?", String.class, playerId))
                .isNull();
    }

    @Test
    void adminUploadForMissingPlayerReturnsNotFound() throws Exception {
        Cookie admin = adminLogin("13900000706");

        mockMvc.perform(multipart("/api/v1/b/players/999999/avatar")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                        .cookie(admin)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void uploadRequiresExpectedPermission() throws Exception {
        Long viewerRole = insertRole("AVATAR_VIEWER", "player:view:any");
        Long viewerId = insertActiveUser("13900000707", viewerRole);
        Long viewerPlayer = insertPlayer(viewerId, "头像只读", 904);
        Cookie viewer = cLogin("13900000707", "Passw0rd!");

        mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                        .cookie(viewer)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        Long readOnlyAdminRole = insertRole("AVATAR_ADMIN_READ", "player:view:any");
        insertActiveUser("13900000708", readOnlyAdminRole);
        Cookie readOnlyAdmin = login("13900000708", "Passw0rd!");

        mockMvc.perform(multipart("/api/v1/b/players/" + viewerPlayer + "/avatar")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                        .cookie(readOnlyAdmin)
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void unauthenticatedUploadIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG))
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void rejectsDisguisedAndUnsupportedFiles() throws Exception {
        Long userId = insertActiveUser("13900000709", roleId("PLAYER"));
        insertPlayer(userId, "头像伪装", 905);
        Cookie session = cLogin("13900000709", "Passw0rd!");

        mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg",
                                "%PDF-1.7 not an image".getBytes(StandardCharsets.US_ASCII)))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41501));

        mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "a.gif", "image/gif",
                                "GIF89a-not-supported".getBytes(StandardCharsets.US_ASCII)))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41501));
    }

    @Test
    void rejectsEmptyCorruptAndOversizedFiles() throws Exception {
        Long userId = insertActiveUser("13900000710", roleId("PLAYER"));
        insertPlayer(userId, "头像校验", 906);
        Cookie session = cLogin("13900000710", "Passw0rd!");

        mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.file").value("文件不能为空"));

        byte[] truncated = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "corrupt.jpg", "image/jpeg", truncated))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.file").value("文件内容损坏"));

        byte[] oversized = new byte[(int) com.yunhefc.player.domain.AvatarImage.MAX_BYTES + 1];
        System.arraycopy(JPEG, 0, oversized, 0, JPEG.length);
        mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.file").value("图片不能超过 2MB"));
    }

    @Test
    void selfUploadThenBindReturnsSignedUrls() throws Exception {
        Long userId = insertActiveUser("13900000711", roleId("PLAYER"));
        Long playerId = insertPlayer(userId, "头像绑定甲", 911);
        Cookie session = cLogin("13900000711", "Passw0rd!");

        MvcResult upload = mockMvc.perform(multipart("/api/v1/c/files/avatar")
                        .file(new MockMultipartFile("file", "bind.jpg", "image/jpeg", JPEG))
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String objectKey = JsonPath.read(upload.getResponse().getContentAsString(), "$.data.objectKey");
        assertThat(objectKey).matches(OBJECT_KEY);

        mockMvc.perform(patch("/api/v1/c/me/player")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"%s\"}".formatted(objectKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("mock://oss/" + objectKey));

        mockMvc.perform(get("/api/v1/c/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.player.avatarUrl").value("mock://oss/" + objectKey));

        assertThat(jdbc.queryForObject("SELECT avatar_url FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo(objectKey);
        String afterJson = jdbc.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')) FROM outbox_event "
                        + "WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ? "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'PLAYER_UPDATE'",
                String.class, playerId);
        assertThat(afterJson).contains(objectKey).doesNotContain("mock://");
    }

    @Test
    void adminUploadThenBindReturnsSignedUrlInList() throws Exception {
        Cookie admin = adminLogin("13900000712");
        Long targetUserId = insertActiveUser("13900000713", roleId("PLAYER"));
        Long playerId = insertPlayer(targetUserId, "头像绑定乙", 912);

        MvcResult upload = mockMvc.perform(multipart("/api/v1/b/players/" + playerId + "/avatar")
                        .file(new MockMultipartFile("file", "bind.webp", "image/webp", WEBP))
                        .cookie(admin)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String objectKey = JsonPath.read(upload.getResponse().getContentAsString(), "$.data.objectKey");
        assertThat(objectKey).matches(OBJECT_KEY);

        mockMvc.perform(patch("/api/v1/b/players/" + playerId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"%s\"}".formatted(objectKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("mock://oss/" + objectKey));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin).param("name", "头像绑定乙"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].avatarUrl").value("mock://oss/" + objectKey));

        assertThat(jdbc.queryForObject("SELECT avatar_url FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo(objectKey);
    }

    private Cookie registerPending(String phone, String name, int position) throws Exception {
        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","purpose":"REGISTER"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"%s","position":%d}
                                """.formatted(phone, name, position)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }

    private Cookie cLogin(String phone, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }

    private Cookie adminLogin(String phone) throws Exception {
        insertActiveUser(phone, roleId("ADMIN"));
        return login(phone, "Passw0rd!");
    }

    private Cookie login(String phone, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }

    private Long insertActiveUser(String phone, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                phone, passwordEncoder.encode("Passw0rd!"));
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private Long insertPlayer(Long userId, String name, int jersey) {
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, ?, 1, 1, '[]', 3)
                """, userId, name, jersey);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertRole(String code, String permissionCode) {
        jdbc.update("INSERT INTO app_role (team_id, code, name, is_default) VALUES (1, ?, ?, 0)", code, code);
        Long roleId = roleId(code);
        jdbc.update("""
                INSERT INTO role_permission (role_id, permission_id)
                SELECT ?, id FROM permission WHERE code = ?
                """, roleId, permissionCode);
        return roleId;
    }

}
