package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class ContentPostIT extends ActivityITSupport {

    private static final String PASSWORD = "Passw0rd!";

    @Test
    void timelineReturnsAuthorsContentAndOrdering() throws Exception {
        Cookie player = player("13900006001", "时间线甲");
        Cookie other = player("13900006002", "时间线乙");
        Long albumId = createAlbum(player, """
                {"title":"时间线相册"}
                """);

        createPost(player, """
                {"content":"  纯文字动态  "}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("纯文字动态"))
                .andExpect(jsonPath("$.data.author.name").value("时间线甲"))
                .andExpect(jsonPath("$.data.author.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.data.album").doesNotExist())
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.likedByMe").value(false));

        createPost(other, """
                {"content":"带换行\\n的正文","albumId":%d}
                """.formatted(albumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("带换行\n的正文"))
                .andExpect(jsonPath("$.data.author.name").value("时间线乙"))
                .andExpect(jsonPath("$.data.album.id").value(albumId))
                .andExpect(jsonPath("$.data.album.title").value("时间线相册"))
                .andExpect(jsonPath("$.data.album.photoCount").value(0));

        mockMvc.perform(get("/api/v1/c/posts").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].author.name").value("时间线乙"))
                .andExpect(jsonPath("$.data.items[1].author.name").value("时间线甲"));

        // 同一时间戳按 id 倒序。
        Long authorId = userId("13900006001");
        jdbc.update("""
                INSERT INTO content_post (team_id, author_user_id, content, created_at)
                VALUES (1, ?, '同刻较早', '2026-01-01 10:00:00.000')
                """, authorId);
        Long firstId = jdbc.queryForObject(
                "SELECT id FROM content_post WHERE content = '同刻较早'", Long.class);
        jdbc.update("""
                INSERT INTO content_post (team_id, author_user_id, content, created_at)
                VALUES (1, ?, '同刻较晚', '2026-01-01 10:00:00.000')
                """, authorId);
        Long secondId = jdbc.queryForObject(
                "SELECT id FROM content_post WHERE content = '同刻较晚'", Long.class);
        assertThat(secondId).isGreaterThan(firstId);

        mockMvc.perform(get("/api/v1/c/posts").param("pageSize", "20").cookie(player))
                .andExpect(jsonPath("$.data.items[2].id").value(secondId))
                .andExpect(jsonPath("$.data.items[3].id").value(firstId));
    }

    @Test
    void createValidatesContentAndReferencedAlbum() throws Exception {
        Cookie player = player("13900006003", "发布校验");
        Long userId = userId("13900006003");
        Long albumId = createAlbum(player, """
                {"title":"引用相册"}
                """);
        jdbc.update("INSERT INTO content_album (team_id, title, created_by) VALUES (2, '跨队相册', ?)",
                userId);
        Long otherTeamAlbumId = jdbc.queryForObject(
                "SELECT id FROM content_album WHERE title = '跨队相册'", Long.class);
        jdbc.update("""
                INSERT INTO content_album (team_id, title, created_by, is_deleted)
                VALUES (1, '已删除相册', ?, 1)
                """, userId);
        Long deletedAlbumId = jdbc.queryForObject(
                "SELECT id FROM content_album WHERE title = '已删除相册'", Long.class);

        createPost(player, """
                {"content":"%s"}
                """.formatted("字".repeat(2000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("字".repeat(2000)));

        createPost(player, """
                {"content":"%s"}
                """.formatted("字".repeat(2001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createPost(player, """
                {"content":"   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createPost(player, """
                {"content":""}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        createPost(player, """
                {"content":"引用可见相册","albumId":%d}
                """.formatted(albumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.album.id").value(albumId));

        for (Long invalidAlbumId : List.of(otherTeamAlbumId, deletedAlbumId, 999999L)) {
            createPost(player, """
                    {"content":"引用无效相册","albumId":%d}
                    """.formatted(invalidAlbumId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }
    }

    @Test
    void postCreationAndDeletionReplayByIdempotencyKey() throws Exception {
        Cookie player = player("13900006030", "幂等动态球员");
        String createKey = "idem-post-create";

        MvcResult first = postWithKey(player, createKey, """
                {"content":"幂等动态"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("幂等动态"))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.likedByMe").value(false))
                .andReturn();
        MvcResult replay = postWithKey(player, createKey, """
                {"content":"幂等动态"}
                """)
                .andExpect(status().isOk())
                .andReturn();

        Long postId = JsonPath.<Number>read(
                first.getResponse().getContentAsString(), "$.data.id").longValue();
        assertThat(JsonPath.<Number>read(
                replay.getResponse().getContentAsString(), "$.data.id").longValue()).isEqualTo(postId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_post WHERE content = '幂等动态'", Integer.class)).isEqualTo(1);

        String deleteKey = "idem-post-delete";
        mockMvc.perform(delete("/api/v1/c/posts/" + postId)
                        .cookie(player)
                        .with(csrf())
                        .header("Idempotency-Key", deleteKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
        mockMvc.perform(delete("/api/v1/c/posts/" + postId)
                        .cookie(player)
                        .with(csrf())
                        .header("Idempotency-Key", deleteKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }

    @Test
    void likeIsIdempotentAndCountsActiveRows() throws Exception {
        Cookie author = player("13900006004", "点赞作者");
        Cookie liker = player("13900006005", "点赞球员");
        Long postId = createPostId(author, "求赞动态");

        like(liker, postId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));
        like(liker, postId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(get("/api/v1/c/posts").cookie(liker))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(true));
        mockMvc.perform(get("/api/v1/c/posts").cookie(author))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(1))
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(false));

        like(author, postId, true)
                .andExpect(jsonPath("$.data.likeCount").value(2));

        like(liker, postId, false)
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(1));
        like(liker, postId, false)
                .andExpect(jsonPath("$.data.likeCount").value(1));
        like(liker, postId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(2));

        // 取消后再点赞恢复同一行，不新增行。
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_post_like WHERE post_id = ?", Integer.class, postId))
                .isEqualTo(2);
        assertThat(activeLikes(postId)).isEqualTo(2);

        // 点赞不产生任何通知。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification", Integer.class)).isZero();

        like(liker, 999999L, true)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(delete("/api/v1/c/posts/" + postId).cookie(author).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isOk());
        like(liker, postId, true)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void concurrentLikeAndUnlikeKeepCountConsistent() throws Exception {
        Cookie author = player("13900006006", "并发点赞作者");
        Cookie liker = player("13900006007", "并发点赞球员");
        Long postId = createPostId(author, "并发点赞动态");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<Integer> like = pool.submit(() -> likeStatus(liker, postId, true, barrier));
            Future<Integer> unlike = pool.submit(() -> likeStatus(liker, postId, false, barrier));
            assertThat(like.get(60, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(unlike.get(60, TimeUnit.SECONDS)).isEqualTo(200);

            CyclicBarrier secondBarrier = new CyclicBarrier(2);
            Future<Integer> first = pool.submit(() -> likeStatus(liker, postId, true, secondBarrier));
            Future<Integer> second = pool.submit(() -> likeStatus(liker, postId, true, secondBarrier));
            assertThat(first.get(60, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(60, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        int expected = activeLikes(postId);
        assertThat(expected).isEqualTo(1);
        mockMvc.perform(get("/api/v1/c/posts").cookie(liker))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(expected));
    }

    @Test
    void deleteRespectsOwnershipAndDoesNotCascade() throws Exception {
        Cookie author = player("13900006008", "删除作者");
        Cookie other = player("13900006009", "删除他人");
        Cookie admin = admin("13900006010");
        Long albumId = createAlbum(author, """
                {"title":"动态相册"}
                """);
        Long postId = createPostId(author, "可删除动态", albumId);
        like(other, postId, true).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/c/posts/" + postId).cookie(other).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(delete("/api/v1/c/posts/" + postId).cookie(author).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        assertThat(jdbc.queryForObject(
                "SELECT is_deleted FROM content_post WHERE id = ?", Integer.class, postId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_album WHERE id = ? AND is_deleted = 0",
                Integer.class, albumId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_post_like WHERE post_id = ? AND is_deleted = 0",
                Integer.class, postId)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/c/posts").cookie(author))
                .andExpect(jsonPath("$.data.total").value(0));

        Long otherPostId = createPostId(other, "他人动态");
        mockMvc.perform(delete("/api/v1/c/posts/" + otherPostId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/c/posts/" + postId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void deletedAlbumLeavesPostReadableWithEmptyAlbum() throws Exception {
        Cookie player = player("13900006011", "软引用球员");
        Cookie admin = admin("13900006012");
        Long albumId = createAlbum(player, """
                {"title":"将被删除的相册"}
                """);
        Long postId = createPostId(player, "相册删除后仍可读", albumId);

        mockMvc.perform(delete("/api/v1/c/albums/" + albumId).cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/c/posts").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(postId))
                .andExpect(jsonPath("$.data.items[0].content").value("相册删除后仍可读"))
                .andExpect(jsonPath("$.data.items[0].album").doesNotExist());
    }

    @Test
    void timelineSupportsOwnPaginationAndAlbumFilter() throws Exception {
        Cookie player = player("13900006013", "动态分页");
        Long albumId = createAlbum(player, """
                {"title":"过滤相册"}
                """);
        for (int i = 0; i < 11; i++) {
            createPostId(player, "分页动态" + i, i == 0 ? albumId : null);
        }

        mockMvc.perform(get("/api/v1/c/posts").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.total").value(11));
        mockMvc.perform(get("/api/v1/c/posts").param("pageSize", "20").cookie(player))
                .andExpect(jsonPath("$.data.items.length()").value(11));
        mockMvc.perform(get("/api/v1/c/posts").param("pageSize", "5").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/c/posts").param("albumId", String.valueOf(albumId)).cookie(player))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].album.id").value(albumId));
    }

    @Test
    void contentIsHiddenFromAnonymousPendingAndGuest() throws Exception {
        Cookie guest = guest("13900006014");
        Cookie pending = pending("13900006015");

        mockMvc.perform(get("/api/v1/c/posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/c/posts").cookie(pending))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));
        mockMvc.perform(get("/api/v1/c/posts").cookie(guest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        createPost(guest, """
                {"content":"外挂发帖"}
                """).andExpect(status().isForbidden());
        createPost(pending, """
                {"content":"待审核发帖"}
                """).andExpect(status().isForbidden());

        Cookie player = player("13900006016", "可见球员");
        Long postId = createPostId(player, "门禁动态");

        mockMvc.perform(put("/api/v1/c/posts/" + postId + "/like")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liked\":true}"))
                .andExpect(status().isUnauthorized());
        like(pending, postId, true).andExpect(status().isForbidden());
        like(guest, postId, true).andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/c/posts/" + postId)
                        .with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/c/posts/" + postId).cookie(pending).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/c/posts/" + postId).cookie(guest).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isForbidden());
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
        insertPlayer(userId, "外挂球员", 21, 0);
        return cLogin(phone, PASSWORD);
    }

    private Cookie pending(String phone) throws Exception {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 0)", phone);
        Long userId = userId(phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)",
                userId, roleId("PLAYER"));
        return cLogin(phone, PASSWORD);
    }

    private ResultActions createPost(Cookie cookie, String body) throws Exception {
        return postWithKey(cookie, idemKey(), body);
    }

    private ResultActions postWithKey(Cookie cookie, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/c/posts")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Long createPostId(Cookie cookie, String content) throws Exception {
        return createPostId(cookie, content, null);
    }

    private Long createPostId(Cookie cookie, String content, Long albumId) throws Exception {
        String body = albumId == null
                ? "{\"content\":\"%s\"}".formatted(content)
                : "{\"content\":\"%s\",\"albumId\":%d}".formatted(content, albumId);
        MvcResult result = createPost(cookie, body).andExpect(status().isOk()).andReturn();
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.data.id").longValue();
    }

    private ResultActions like(Cookie cookie, Long postId, boolean liked) throws Exception {
        return mockMvc.perform(put("/api/v1/c/posts/" + postId + "/like")
                .cookie(cookie)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"liked\":%s}".formatted(liked)));
    }

    private int likeStatus(Cookie cookie, Long postId, boolean liked, CyclicBarrier barrier) throws Exception {
        barrier.await(30, TimeUnit.SECONDS);
        return like(cookie, postId, liked).andReturn().getResponse().getStatus();
    }

    private Long createAlbum(Cookie cookie, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/c/albums")
                        .cookie(cookie)
                        .with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.data.id").longValue();
    }

    private int activeLikes(Long postId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM content_post_like
                WHERE post_id = ? AND is_deleted = 0
                """, Integer.class, postId);
    }
}
