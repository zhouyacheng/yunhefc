package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhefc.auth.application.InviteService;
import com.yunhefc.auth.application.RoleService;
import com.yunhefc.auth.domain.InviteLink;
import com.yunhefc.auth.dto.RoleDtos.UserRolesRequest;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.security.AuthPrincipal;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class M01ConcurrencyIT extends IntegrationTestSupport {

    @Autowired
    InviteService inviteService;

    @Autowired
    RoleService roleService;

    @Test
    void revokedInviteCannotBeConsumedFromStaleSnapshot() throws Exception {
        Long userId = insertUser("13900000301", 0);
        insertPlayer(userId, "撤销并发球员");
        String token = "stale-revoke-token";
        Long inviteId = insertMatchInvite(token, userId);

        InviteLink stale = inviteService.findUsableForAccept(token, userId);
        withPrincipal(userId, "invite:manage:any", () -> inviteService.revoke(inviteId));

        assertThatThrownBy(() -> inviteService.consume(stale, userId))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVITE_UNAVAILABLE));
        assertThat(usedCount(inviteId)).isZero();
        assertThat(inviteUseCount(inviteId)).isZero();
    }

    @Test
    void expiredInviteCannotBeConsumedFromStaleSnapshot() throws Exception {
        Long userId = insertUser("13900000302", 0);
        insertPlayer(userId, "过期并发球员");
        String token = "stale-expired-token";
        Long inviteId = insertMatchInvite(token, userId);

        InviteLink stale = inviteService.findUsableForAccept(token, userId);
        jdbc.update("UPDATE invite_link SET expires_at = DATE_SUB(NOW(3), INTERVAL 1 DAY) WHERE id = ?",
                inviteId);

        assertThatThrownBy(() -> inviteService.consume(stale, userId))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVITE_UNAVAILABLE));
        assertThat(usedCount(inviteId)).isZero();
        assertThat(inviteUseCount(inviteId)).isZero();
    }

    @Test
    void revokeDoesNotOverwriteUsedCount() throws Exception {
        Long userId = insertUser("13900000303", 0);
        String token = "revoke-used-count-token";
        Long inviteId = insertMatchInvite(token, userId);
        jdbc.update("UPDATE invite_link SET used_count = 3 WHERE id = ?", inviteId);

        withPrincipal(userId, "invite:manage:any", () -> inviteService.revoke(inviteId));

        assertThat(inviteStatus(inviteId)).isEqualTo(1);
        assertThat(usedCount(inviteId)).isEqualTo(3);
    }

    @Test
    void concurrentRevokeAndAcceptNeverCountRevokedInvite() throws Exception {
        Long adminId = insertUser("13900000321", 1);
        assignRole(adminId, "ADMIN");
        Cookie admin = bLogin("13900000321");
        Long userId = insertUser("13900000322", 0);
        insertPlayer(userId, "邀请并发球员");
        Cookie user = cLogin("13900000322");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 10; i++) {
                String token = "concurrent-invite-" + i;
                String idempotencyKey = "revoke-" + i;
                Long inviteId = insertMatchInvite(token, adminId);
                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<Integer> accept = pool.submit(() -> postStatus(user,
                        "/api/v1/c/invites/" + token + "/accept", null, barrier));
                Future<Integer> revoke = pool.submit(() -> postStatus(admin,
                        "/api/v1/b/invites/" + inviteId + "/revoke", idempotencyKey, barrier));

                int acceptStatus = accept.get(30, TimeUnit.SECONDS);
                int revokeStatus = revoke.get(30, TimeUnit.SECONDS);

                assertThat(revokeStatus).isEqualTo(200);
                assertThat(inviteStatus(inviteId)).isEqualTo(1);
                int used = usedCount(inviteId);
                assertThat(inviteUseCount(inviteId)).isEqualTo(used);
                if (acceptStatus == 200) {
                    assertThat(used).isEqualTo(1);
                } else {
                    assertThat(acceptStatus).isEqualTo(409);
                    assertThat(used).isZero();
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentMutualAdminDemotionKeepsAtLeastOneAdmin() throws Exception {
        Long playerRoleId = roleId("PLAYER");
        Long userA = insertUser("13900000331", 1);
        Long userB = insertUser("13900000332", 1);
        assignRole(userA, "ADMIN");
        assignRole(userB, "ADMIN");
        removeOtherAdminGrants(userA, userB);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<Object> aDemotesB = pool.submit(
                    () -> assignRolesAs(userA, userB, playerRoleId, barrier));
            Future<Object> bDemotesA = pool.submit(
                    () -> assignRolesAs(userB, userA, playerRoleId, barrier));
            Object first = aDemotesB.get(30, TimeUnit.SECONDS);
            Object second = bDemotesA.get(30, TimeUnit.SECONDS);

            Object failure = first instanceof BusinessException ? first : second;
            Object success = first instanceof BusinessException ? second : first;
            assertThat(success).isEqualTo("ok");
            assertThat(failure).isInstanceOfSatisfying(BusinessException.class,
                    ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED));
            assertThat(adminUserIds()).hasSize(1).containsAnyOf(userA, userB);
        } finally {
            pool.shutdownNow();
        }
    }

    private Object assignRolesAs(Long actorId, Long targetId, Long playerRoleId,
                                 CyclicBarrier barrier) throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal(actorId, "role:manage:any"), null,
                List.of(new SimpleGrantedAuthority("role:manage:any"))));
        SecurityContextHolder.setContext(context);
        try {
            barrier.await(5, TimeUnit.SECONDS);
            roleService.assignRoles(targetId, new UserRolesRequest(List.of(playerRoleId)));
            return "ok";
        } catch (BusinessException ex) {
            return ex;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private int postStatus(Cookie cookie, String path, String idempotencyKey,
                           CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        var request = post(path).cookie(cookie).with(csrf());
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private void withPrincipal(Long userId, String permission, Runnable action) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal(userId, permission), null,
                List.of(new SimpleGrantedAuthority(permission))));
        SecurityContextHolder.setContext(context);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AuthPrincipal principal(Long userId, String permission) {
        return new AuthPrincipal(userId, "13900000300", 1, 1L, "并发测试",
                Set.of("ADMIN"), Set.of(permission));
    }

    private Long insertUser(String phone, int status) {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, ?)",
                phone, passwordEncoder.encode("Passw0rd!"), status);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private void insertPlayer(Long userId, String name) {
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, skill)
                VALUES (1, ?, ?, 0, 2, 3)
                """, userId, name);
    }

    private Long insertMatchInvite(String token, Long inviterUserId) {
        jdbc.update("""
                INSERT INTO invite_link (team_id, type, token_hash, match_id, inviter_user_id,
                    expires_at, max_uses, used_count, status)
                VALUES (1, 1, SHA2(?, 256), NULL, ?, DATE_ADD(NOW(3), INTERVAL 1 DAY), 1, 0, 0)
                """, token, inviterUserId);
        return jdbc.queryForObject(
                "SELECT id FROM invite_link WHERE token_hash = SHA2(?, 256)", Long.class, token);
    }

    private void assignRole(Long userId, String roleCode) {
        Long roleId = roleId(roleCode);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private Long roleId(String roleCode) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = ? AND team_id = 1", Long.class, roleCode);
    }

    private void removeOtherAdminGrants(Long userA, Long userB) {
        jdbc.update("""
                UPDATE user_role SET is_deleted = 1
                WHERE is_deleted = 0 AND user_id NOT IN (?, ?)
                  AND role_id IN (
                    SELECT rp.role_id FROM role_permission rp
                    JOIN permission p ON p.id = rp.permission_id
                    WHERE rp.is_deleted = 0 AND p.is_deleted = 0 AND p.code = 'role:manage:any')
                """, userA, userB);
    }

    private List<Long> adminUserIds() {
        return jdbc.queryForList("""
                SELECT DISTINCT u.id
                FROM app_user u
                JOIN user_role ur ON ur.user_id = u.id AND ur.is_deleted = 0
                JOIN role_permission rp ON rp.role_id = ur.role_id AND rp.is_deleted = 0
                JOIN permission p ON p.id = rp.permission_id AND p.is_deleted = 0
                WHERE u.status = 1 AND u.is_deleted = 0 AND p.code = 'role:manage:any'
                """, Long.class);
    }

    private Integer inviteStatus(Long inviteId) {
        return jdbc.queryForObject("SELECT status FROM invite_link WHERE id = ?", Integer.class, inviteId);
    }

    private Integer usedCount(Long inviteId) {
        return jdbc.queryForObject("SELECT used_count FROM invite_link WHERE id = ?",
                Integer.class, inviteId);
    }

    private Integer inviteUseCount(Long inviteId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM invite_use WHERE invite_id = ?", Integer.class, inviteId);
    }

    private Cookie cLogin(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Passw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }

    private Cookie bLogin(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Passw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }
}
