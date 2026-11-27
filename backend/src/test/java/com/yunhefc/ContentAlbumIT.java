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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import com.yunhefc.common.util.Ulid;
import com.yunhefc.content.domain.ContentRules;
import com.yunhefc.infra.storage.ObjectRemoval;
import com.yunhefc.infra.storage.InMemoryOssGateway;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

class ContentAlbumIT extends ActivityITSupport {

    private static final long PHOTO_MAX = 5_242_880L;
    private static final String PASSWORD = "Passw0rd!";
    private static final Pattern PHOTO_KEY =
            Pattern.compile("^content/\\d+/[0-9A-HJKMNP-TV-Z]{26}\\.(jpg|png|webp)$");
    private static final Logger DELETION_LOGGER =
            (Logger) LoggerFactory.getLogger(ObjectRemoval.class);

    @Autowired
    private InMemoryOssGateway ossGateway;

    @Test
    void createsAlbumTrimsTitleAndReturnsEmptyCover() throws Exception {
        Cookie player = player("13900005001", "相册创建人");

        MvcResult created = postAlbum(player, """
                {"title":"  赛后合影  "}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("赛后合影"))
                .andExpect(jsonPath("$.data.matchId").doesNotExist())
                .andExpect(jsonPath("$.data.activityId").doesNotExist())
                .andExpect(jsonPath("$.data.ownerName").doesNotExist())
                .andExpect(jsonPath("$.data.coverThumbnailUrl").doesNotExist())
                .andExpect(jsonPath("$.data.coverUrl").doesNotExist())
                .andExpect(jsonPath("$.data.photoCount").value(0))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andReturn();
        Long albumId = jsonLong(created, "$.data.id");

        mockMvc.perform(get("/api/v1/c/albums/" + albumId).cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(albumId));

        mockMvc.perform(get("/api/v1/c/albums").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(albumId))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/v1/c/albums/999999").cookie(player))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        // 名称长度按去除首尾空白后计算（05 CNT-04）。
        postAlbum(player, """
                {"title":"  %s  "}
                """.formatted("名".repeat(64)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("名".repeat(64)));
    }

    @Test
    void rejectsBothOwnersCrossTeamOwnersAndUnknownRelations() throws Exception {
        Cookie player = player("13900005002", "归属校验球员");
        Long matchId = insertMatch(1, "主队比赛");
        Long activityId = insertActivity("主队活动", futureAt(5, 10), 0, 50,
                futureAt(4, 10), 1L);
        Long otherMatchId = insertMatch(2, "跨队比赛");
        Long otherActivityId = insertActivity("跨队活动", futureAt(5, 10), 0, 50,
                futureAt(4, 10), 2L);

        postAlbum(player, """
                {"title":"两个归属","matchId":%d,"activityId":%d}
                """.formatted(matchId, activityId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        postAlbum(player, """
                {"title":"跨队比赛","matchId":%d}
                """.formatted(otherMatchId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        postAlbum(player, """
                {"title":"跨队活动","activityId":%d}
                """.formatted(otherActivityId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        postAlbum(player, """
                {"title":"不存在","matchId":999999}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        postAlbum(player, """
                {"title":"比赛相册","matchId":%d}
                """.formatted(matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerName").value("主队比赛"));

        postAlbum(player, """
                {"title":"活动相册","activityId":%d}
                """.formatted(activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerName").value("主队活动"));
    }

    @Test
    void ownerNameIsNullWhenOwnerIsNoLongerVisible() throws Exception {
        Cookie player = player("13900005020", "归属失效球员");
        Long matchId = insertMatch(1, "随后软删的比赛");
        Long albumId = jsonLong(postAlbum(player, """
                {"title":"归属失效相册","matchId":%d}
                """.formatted(matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerName").value("随后软删的比赛"))
                .andReturn(), "$.data.id");

        jdbc.update("UPDATE team_match SET is_deleted = 1 WHERE id = ?", matchId);

        mockMvc.perform(get("/api/v1/c/albums/" + albumId).cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchId").value(matchId))
                .andExpect(jsonPath("$.data.ownerName").doesNotExist());
        mockMvc.perform(get("/api/v1/c/albums").cookie(player))
                .andExpect(jsonPath("$.data.items[0].ownerName").doesNotExist());
    }

    @Test
    void updatesTitleOwnershipAndCover() throws Exception {
        Cookie player = player("13900005003", "相册编辑球员");
        Cookie admin = admin("13900005004");
        Long matchId = insertMatch(1, "编辑用比赛");
        Long albumId = jsonLong(postAlbum(player, """
                {"title":"旧名称"}
                """).andExpect(status().isOk()).andReturn(), "$.data.id");
        Long otherAlbumId = jsonLong(postAlbum(player, """
                {"title":"另一个相册"}
                """).andExpect(status().isOk()).andReturn(), "$.data.id");
        String firstThumb = uploadPhoto(player, albumId, JPEG, "image/jpeg")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondThumb = uploadPhoto(player, albumId, PNG, "image/png")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long firstPhotoId = jsonLong(firstThumb, "$.data.photo.id");
        Long secondPhotoId = jsonLong(secondThumb, "$.data.photo.id");
        String firstThumbnailUrl = JsonPath.read(firstThumb, "$.data.photo.thumbnailUrl");
        String secondThumbnailUrl = JsonPath.read(secondThumb, "$.data.photo.thumbnailUrl");

        // 无显式封面时取最早照片。
        mockMvc.perform(get("/api/v1/c/albums/" + albumId).cookie(player))
                .andExpect(jsonPath("$.data.coverThumbnailUrl").value(firstThumbnailUrl))
                .andExpect(jsonPath("$.data.coverUrl").value("mock://oss/" + objectKeyOf(firstThumb)));

        patchAlbum(admin, albumId, """
                {"title":" 新名称 ","coverPhotoId":%d,"matchId":%d}
                """.formatted(secondPhotoId, matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新名称"))
                .andExpect(jsonPath("$.data.matchId").value(matchId))
                .andExpect(jsonPath("$.data.ownerName").value("编辑用比赛"))
                .andExpect(jsonPath("$.data.coverThumbnailUrl").value(secondThumbnailUrl));

        patchAlbum(admin, albumId, """
                {"coverPhotoId":%d}
                """.formatted(firstPhotoId))
                .andExpect(jsonPath("$.data.coverThumbnailUrl").value(firstThumbnailUrl));

        // 封面必须属于本相册。
        String otherThumb = uploadPhoto(player, otherAlbumId, JPEG, "image/jpeg")
                .andReturn().getResponse().getContentAsString();
        patchAlbum(admin, albumId, """
                {"coverPhotoId":%d}
                """.formatted(jsonLong(otherThumb, "$.data.photo.id")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        // 显式清空归属。
        patchAlbum(admin, albumId, """
                {"matchId":null}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchId").doesNotExist())
                .andExpect(jsonPath("$.data.ownerName").doesNotExist());

        // 切换归属须显式清空另一侧，否则按「至多一个非空」拒绝。
        Long activityId = insertActivity("编辑用活动", futureAt(6, 10), 0, 50,
                futureAt(5, 10), 1L);
        patchAlbum(admin, albumId, """
                {"activityId":%d}
                """.formatted(activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerName").value("编辑用活动"));
        patchAlbum(admin, albumId, """
                {"matchId":%d}
                """.formatted(matchId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        patchAlbum(admin, albumId, """
                {"matchId":%d,"activityId":null}
                """.formatted(matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerName").value("编辑用比赛"))
                .andExpect(jsonPath("$.data.activityId").doesNotExist());

        // 非管理员不能编辑相册。
        patchAlbum(player, albumId, """
                {"title":"球员改名"}
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void uploadValidatesTypeSizeAndSigning() throws Exception {
        Cookie player = player("13900005005", "上传球员");
        Long albumId = jsonLong(postAlbum(player, """
                {"title":"上传相册"}
                """).andExpect(status().isOk()).andReturn(), "$.data.id");

        MvcResult uploaded = uploadPhoto(player, albumId, JPEG, "image/jpeg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoCount").value(1))
                .andExpect(jsonPath("$.data.photo.uploadedBy").isNumber())
                .andExpect(jsonPath("$.data.photo.uploaderName").value("上传球员"))
                .andReturn();
        String key = objectKeyOf(uploaded);
        assertThat(key).matches(PHOTO_KEY);
        String thumbnailUrl = JsonPath.read(uploaded.getResponse().getContentAsString(),
                "$.data.photo.thumbnailUrl");
        String originalUrl = JsonPath.read(uploaded.getResponse().getContentAsString(),
                "$.data.photo.originalUrl");
        assertThat(thumbnailUrl).contains("x-oss-process=" + ContentRules.THUMBNAIL_PROCESS);
        assertThat(originalUrl).doesNotContain("x-oss-process");
        assertThat(originalUrl).isEqualTo("mock://oss/" + key);
        // 不落额外缩略图对象。
        assertThat(ossGateway.exists(key)).isTrue();
        assertThat(ossGateway.contentType(key)).isEqualTo("image/jpeg");

        // 伪造 Content-Type 不改变嗅探结果。
        MvcResult disguised = uploadPhoto(player, albumId, JPEG, "image/png")
                .andExpect(status().isOk()).andReturn();
        assertThat(objectKeyOf(disguised)).endsWith(".jpg");

        uploadPhoto(player, albumId, "not an image".getBytes(StandardCharsets.UTF_8), "image/png")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41501));

        uploadPhoto(player, albumId, jpegOfSize(PHOTO_MAX), "image/jpeg")
                .andExpect(status().isOk());
        uploadPhoto(player, albumId, jpegOfSize(PHOTO_MAX + 1), "image/jpeg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.file").value("照片不能超过 5MB"));
    }

    @Test
    void enforcesTwoHundredPhotosPerAlbum() throws Exception {
        Cookie player = player("13900005006", "容量球员");
        Long albumId = jsonLong(postAlbum(player, """
                {"title":"满员相册"}
                """).andExpect(status().isOk()).andReturn(), "$.data.id");
        Long userId = userId("13900005006");
        insertPhotos(albumId, userId, 199);

        uploadPhoto(player, albumId, JPEG, "image/jpeg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoCount").value(200));

        uploadPhoto(player, albumId, JPEG, "image/jpeg")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40920));
        assertThat(activePhotos(albumId)).isEqualTo(200);
    }

    @Test
    void concurrentUploadsNeverExceedTwoHundred() throws Exception {
        Cookie player = player("13900005007", "并发上传球员");
        Long albumId = jsonLong(postAlbum(player, """
                {"title":"并发相册"}
                """).andExpect(status().isOk()).andReturn(), "$.data.id");
        insertPhotos(albumId, userId("13900005007"), 199);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<Integer> first = pool.submit(() -> uploadStatus(player, albumId, barrier));
            Future<Integer> second = pool.submit(() -> uploadStatus(player, albumId, barrier));
            List<Integer> statuses = new ArrayList<>(List.of(
                    first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS)));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(activePhotos(albumId)).isEqualTo(200);
    }

    @Test
    void deletesOwnPhotosAndFallsBackFromCover() throws Exception {
        Cookie owner = player("13900005008", "上传人");
        Cookie otherPlayer = player("13900005009", "其他球员");
        Cookie admin = admin("13900005010");
        Long albumId = jsonLong(postAlbum(owner, """
                {"title":"删除相册"}
                """).andExpect(status().isOk()).andReturn(), "$.data.id");

        Long firstPhotoId = uploadAndGetPhotoId(owner, albumId, JPEG);
        Long secondPhotoId = uploadAndGetPhotoId(owner, albumId, PNG);
        Long thirdPhotoId = uploadAndGetPhotoId(owner, albumId, JPEG);
        patchAlbum(admin, albumId, """
                {"coverPhotoId":%d}
                """.formatted(thirdPhotoId)).andExpect(status().isOk());
        String thirdKey = photoKey(thirdPhotoId);

        // 本人删除非封面照片。
        deletePhoto(owner, albumId, firstPhotoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.coverReset").value(false));
        assertThat(ossGateway.exists(photoKey(firstPhotoId))).isFalse();

        // 他人不能删除，管理员可以。
        deletePhoto(otherPlayer, albumId, thirdPhotoId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        deletePhoto(admin, albumId, thirdPhotoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverReset").value(true));
        assertThat(ossGateway.exists(thirdKey)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT cover_photo_id FROM content_album WHERE id = ?", Long.class, albumId)).isNull();

        // 封面回退到剩余最早照片。
        String secondThumb = JsonPath.read(mockMvc.perform(
                        get("/api/v1/c/albums/" + albumId + "/photos").cookie(owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.items[0].thumbnailUrl");
        mockMvc.perform(get("/api/v1/c/albums/" + albumId).cookie(owner))
                .andExpect(jsonPath("$.data.coverThumbnailUrl").value(secondThumb))
                .andExpect(jsonPath("$.data.photoCount").value(1));
    }

    @Test
    void deletingImplicitEarliestPhotoReportsCoverReset() throws Exception {
        Cookie player = player("13900005017", "隐式封面球员");
        Long albumId = jsonLong(postAlbum(player, """
                {"title":"隐式封面相册"}
                """).andExpect(status().isOk()).andReturn(), "$.data.id");
        Long firstPhotoId = uploadAndGetPhotoId(player, albumId, JPEG);
        Long secondPhotoId = uploadAndGetPhotoId(player, albumId, PNG);
        String secondThumb = JsonPath.read(mockMvc.perform(
                        get("/api/v1/c/albums/" + albumId + "/photos").cookie(player))
                .andReturn().getResponse().getContentAsString(), "$.data.items[1].thumbnailUrl");

        // 未显式设置封面时，最早照片就是有效封面。
        deletePhoto(player, albumId, firstPhotoId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverReset").value(true));
        mockMvc.perform(get("/api/v1/c/albums/" + albumId).cookie(player))
                .andExpect(jsonPath("$.data.coverThumbnailUrl").value(secondThumb))
                .andExpect(jsonPath("$.data.photoCount").value(1));

        deletePhoto(player, albumId, secondPhotoId)
                .andExpect(jsonPath("$.data.coverReset").value(true));
        mockMvc.perform(get("/api/v1/c/albums/" + albumId).cookie(player))
                .andExpect(jsonPath("$.data.coverThumbnailUrl").doesNotExist())
                .andExpect(jsonPath("$.data.photoCount").value(0));
    }

    @Test
    void ossDeleteFailureStillDeletesAlbumAndLogsWarning() throws Exception {
        Cookie player = player("13900005018", "OSS 降级球员");
        Long albumId = jsonLong(postAlbum(player, """
                {"title":"OSS 降级相册"}
                """).andExpect(status().isOk()).andReturn(), "$.data.id");
        Long firstPhotoId = uploadAndGetPhotoId(player, albumId, JPEG);
        Long secondPhotoId = uploadAndGetPhotoId(player, albumId, PNG);
        String firstKey = photoKey(firstPhotoId);
        String failingKey = photoKey(secondPhotoId);
        ossGateway.failDeleteOn(failingKey);

        Cookie admin = admin("13900005019");
        ListAppender<ILoggingEvent> appender = attachDeletionAppender();
        try {
            mockMvc.perform(delete("/api/v1/c/albums/" + albumId).cookie(admin).with(csrf())
                            .header("Idempotency-Key", idemKey()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.deletedPhotos").value(2));
        } finally {
            DELETION_LOGGER.detachAppender(appender);
        }

        assertThat(ossGateway.exists(failingKey)).isTrue();
        assertThat(ossGateway.exists(firstKey)).isFalse();
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(failingKey);
        });
        mockMvc.perform(get("/api/v1/c/albums/" + albumId).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAlbumSoftDeletesPhotosAndRemovesObjects() throws Exception {
        Cookie player = player("13900005011", "删相册球员");
        Long albumId = jsonLong(postAlbum(player, """
                {"title":"待删相册"}
                """).andExpect(status().isOk()).andReturn(), "$.data.id");
        Long firstPhotoId = uploadAndGetPhotoId(player, albumId, JPEG);
        Long secondPhotoId = uploadAndGetPhotoId(player, albumId, PNG);
        String firstKey = photoKey(firstPhotoId);
        String secondKey = photoKey(secondPhotoId);

        mockMvc.perform(delete("/api/v1/c/albums/" + albumId).cookie(player).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isForbidden());

        Cookie admin = admin("13900005012");
        mockMvc.perform(delete("/api/v1/c/albums/" + albumId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.deletedPhotos").value(2));

        mockMvc.perform(get("/api/v1/c/albums/" + albumId).cookie(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/c/albums/" + albumId + "/photos").cookie(admin))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_album WHERE id = ? AND is_deleted = 1",
                Integer.class, albumId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_album_photo WHERE album_id = ? AND is_deleted = 1",
                Integer.class, albumId)).isEqualTo(2);
        assertThat(ossGateway.exists(firstKey)).isFalse();
        assertThat(ossGateway.exists(secondKey)).isFalse();
    }

    @Test
    void contentIsHiddenFromAnonymousPendingAndGuest() throws Exception {
        Cookie guest = guest("13900005013");
        Cookie pending = pending("13900005014");

        mockMvc.perform(get("/api/v1/c/albums"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/c/albums").cookie(pending))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));
        mockMvc.perform(get("/api/v1/c/albums").cookie(guest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        Cookie player = player("13900005015", "正常球员");
        postAlbum(guest, """
                {"title":"外挂建相册"}
                """).andExpect(status().isForbidden());
        postAlbum(player, """
                {"title":"正常相册"}
                """).andExpect(status().isOk());
    }

    @Test
    void photoListUsesOwnPageSizesAndOwnerFilter() throws Exception {
        Cookie player = player("13900005016", "分页球员");
        Long matchId = insertMatch(1, "分页比赛");
        Long albumId = jsonLong(postAlbum(player, """
                {"title":"分页相册","matchId":%d}
                """.formatted(matchId)).andExpect(status().isOk()).andReturn(), "$.data.id");
        insertPhotos(albumId, userId("13900005016"), 31);

        mockMvc.perform(get("/api/v1/c/albums/" + albumId + "/photos").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(30))
                .andExpect(jsonPath("$.data.total").value(31));
        mockMvc.perform(get("/api/v1/c/albums/" + albumId + "/photos")
                        .param("pageSize", "60").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(31));
        mockMvc.perform(get("/api/v1/c/albums/" + albumId + "/photos")
                        .param("pageSize", "10").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/c/albums").param("matchId", String.valueOf(matchId)).cookie(player))
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/c/albums").param("matchId", "999999").cookie(player))
                .andExpect(jsonPath("$.data.total").value(0));
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
        insertPlayer(userId, "外挂球员", 11, 0);
        return cLogin(phone, PASSWORD);
    }

    private Cookie pending(String phone) throws Exception {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 0)", phone);
        Long userId = userId(phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)",
                userId, roleId("PLAYER"));
        return cLogin(phone, PASSWORD);
    }

    private Long insertMatch(long teamId, String title) {
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status,
                    manual_status, capacity, season_match_no, total_match_no, created_by)
                VALUES (?, ?, 1, NOW(3) + INTERVAL 2 HOUR, NOW(3) + INTERVAL 4 HOUR,
                    NOW(3), NOW(3) + INTERVAL 1 HOUR, '测试场地', '2099', '橙队', '兰队',
                    2, 1, 30, 900, 900, 1)
                """, teamId, title);
        return jdbc.queryForObject("SELECT id FROM team_match WHERE title = ?", Long.class, title);
    }

    private ResultActions postAlbum(Cookie cookie, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/c/albums")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", idemKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patchAlbum(Cookie cookie, Long albumId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/c/albums/" + albumId)
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", idemKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions deletePhoto(Cookie cookie, Long albumId, Long photoId) throws Exception {
        return mockMvc.perform(delete("/api/v1/c/albums/" + albumId + "/photos/" + photoId)
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", idemKey()));
    }

    private ResultActions uploadPhoto(Cookie cookie, Long albumId, byte[] bytes,
                                      String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo", contentType, bytes);
        return mockMvc.perform(multipart("/api/v1/c/albums/" + albumId + "/photos")
                .file(file)
                .cookie(cookie)
                .with(csrf()));
    }

    private int uploadStatus(Cookie cookie, Long albumId, CyclicBarrier barrier) throws Exception {
        barrier.await(30, TimeUnit.SECONDS);
        return uploadPhoto(cookie, albumId, JPEG, "image/jpeg")
                .andReturn().getResponse().getStatus();
    }

    private Long uploadAndGetPhotoId(Cookie cookie, Long albumId, byte[] bytes) throws Exception {
        MvcResult result = uploadPhoto(cookie, albumId, bytes, "image/jpeg")
                .andExpect(status().isOk())
                .andReturn();
        return jsonLong(result, "$.data.photo.id");
    }

    private String photoKey(Long photoId) {
        return jdbc.queryForObject(
                "SELECT object_key FROM content_album_photo WHERE id = ?", String.class, photoId);
    }

    private String objectKeyOf(String json) {
        String originalUrl = JsonPath.read(json, "$.data.photo.originalUrl");
        return originalUrl.substring("mock://oss/".length());
    }

    private String objectKeyOf(MvcResult result) throws Exception {
        return objectKeyOf(result.getResponse().getContentAsString());
    }

    private void insertPhotos(Long albumId, Long userId, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update("""
                    INSERT INTO content_album_photo (album_id, object_key, uploaded_by)
                    VALUES (?, ?, ?)
                    """, albumId, "content/" + userId + "/" + Ulid.generate() + ".jpg", userId);
        }
    }

    private int activePhotos(Long albumId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_album_photo WHERE album_id = ? AND is_deleted = 0",
                Integer.class, albumId);
    }

    private ListAppender<ILoggingEvent> attachDeletionAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        DELETION_LOGGER.addAppender(appender);
        return appender;
    }

    private byte[] jpegOfSize(long size) {
        byte[] content = new byte[(int) size];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        content[content.length - 2] = (byte) 0xFF;
        content[content.length - 1] = (byte) 0xD9;
        return content;
    }

    private Long jsonLong(String json, String path) {
        return JsonPath.<Number>read(json, path).longValue();
    }

    private Long jsonLong(MvcResult result, String path) throws Exception {
        return jsonLong(result.getResponse().getContentAsString(), path);
    }
}
