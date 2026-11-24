package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

class LedgerIT extends ActivityITSupport {

    private static final String OCCURRED_AT = "2026-11-20T20:10:00+08:00";

    // ---------- LED-01~11 ----------

    @Test
    void createListSummaryAndAliasesMatch() throws Exception {
        Cookie admin = bAdminLogin("13900004001");
        Cookie player = player("13900004002", "账本球员", 901);
        Long playerId = playerId("13900004002");
        Long activityId = insertActivity("账本聚餐", futureAt(20, 18), 0, 10, futureAt(20, 17), 1);

        MvcResult created = createEntry(admin, entryJson(0, categoryId("队费"), "300.00",
                "11 月队费", OCCURRED_AT, playerId, activityId), idemKey())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value(0))
                .andExpect(jsonPath("$.data.typeLabel").value("INCOME"))
                .andExpect(jsonPath("$.data.categoryId").value(categoryId("队费")))
                .andExpect(jsonPath("$.data.categoryName").value("队费"))
                .andExpect(jsonPath("$.data.categoryDirection").value(0))
                .andExpect(jsonPath("$.data.amount").value(300.00))
                .andExpect(jsonPath("$.data.description").value("11 月队费"))
                .andExpect(jsonPath("$.data.occurredAt").value(OCCURRED_AT))
                .andExpect(jsonPath("$.data.playerId").value(playerId))
                .andExpect(jsonPath("$.data.playerName").value("账本球员"))
                .andExpect(jsonPath("$.data.activityId").value(activityId))
                .andExpect(jsonPath("$.data.activityTitle").value("账本聚餐"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();
        Long entryId = JsonPath.<Number>read(
                created.getResponse().getContentAsString(), "$.data.id").longValue();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'LEDGER_CREATE'
                """, Integer.class, entryId)).isEqualTo(1);

        MvcResult cList = mockMvc.perform(get("/api/v1/c/ledger/entries").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(entryId))
                .andReturn();
        MvcResult bList = mockMvc.perform(get("/api/v1/b/ledger/entries").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat((Object) JsonPath.read(bList.getResponse().getContentAsString(), "$.data"))
                .isEqualTo(JsonPath.read(cList.getResponse().getContentAsString(), "$.data"));

        MvcResult cSummary = mockMvc.perform(get("/api/v1/c/ledger/summary").cookie(player))
                .andExpect(jsonPath("$.data.income").value(300.00))
                .andExpect(jsonPath("$.data.expense").value(0.00))
                .andExpect(jsonPath("$.data.balance").value(300.00))
                .andReturn();
        MvcResult bSummary = mockMvc.perform(get("/api/v1/b/ledger/summary").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat((Object) JsonPath.read(bSummary.getResponse().getContentAsString(), "$.data"))
                .isEqualTo(JsonPath.read(cSummary.getResponse().getContentAsString(), "$.data"));
    }

    @Test
    void rejectsInvalidAmounts() throws Exception {
        Cookie admin = bAdminLogin("13900004011");
        Long categoryId = categoryId("队费");
        for (String amount : List.of("0", "-1", "100000000", "10.123")) {
            createEntry(admin, entryJson(0, categoryId, amount, "非法金额", OCCURRED_AT, null, null),
                    idemKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }
        createEntry(admin, entryJson(0, categoryId, "99999999.99", "上限金额", OCCURRED_AT,
                null, null), idemKey()).andExpect(status().isOk());
        createEntry(admin, entryJson(0, categoryId, "300.0", "一位小数", OCCURRED_AT,
                null, null), idemKey())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(300.00));
    }

    @Test
    void enforcesCategoryDirectionExceptUniversal() throws Exception {
        Cookie admin = bAdminLogin("13900004021");
        Long venue = categoryId("场地");
        Long membership = categoryId("队费");
        Long universal = categoryId("其他");

        createEntry(admin, entryJson(0, venue, "100.00", "收入配场地", OCCURRED_AT, null, null),
                idemKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createEntry(admin, entryJson(1, membership, "100.00", "支出配队费", OCCURRED_AT, null, null),
                idemKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        createEntry(admin, entryJson(0, universal, "100.00", "通用收入", OCCURRED_AT, null, null),
                idemKey()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryDirection").value((Object) null));
        createEntry(admin, entryJson(1, universal, "50.00", "通用支出", OCCURRED_AT, null, null),
                idemKey()).andExpect(status().isOk());
    }

    @Test
    void customCategoryLifecycle() throws Exception {
        Cookie admin = bAdminLogin("13900004031");
        Cookie player = player("13900004032", "分类阅读者", 902);

        MvcResult created = createCategory(admin, """
                {"name":" 球衣 ","direction":1,"sortNo":8}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("球衣"))
                .andExpect(jsonPath("$.data.direction").value(1))
                .andExpect(jsonPath("$.data.directionLabel").value("EXPENSE"))
                .andExpect(jsonPath("$.data.sortNo").value(8))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.entryCount").value(0))
                .andReturn();
        Long jerseyId = JsonPath.<Number>read(
                created.getResponse().getContentAsString(), "$.data.id").longValue();

        createCategory(admin, """
                {"name":"球衣","direction":1}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        Long jerseyEntryId = JsonPath.<Number>read(createEntry(admin,
                        entryJson(1, jerseyId, "240.00", "球衣采购", OCCURRED_AT, null, null), idemKey())
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id").longValue();

        mockMvc.perform(patch("/api/v1/b/ledger/categories/" + jerseyId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isActive":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false))
                .andExpect(jsonPath("$.data.entryCount").value(1));

        // 分类未变更时，停用分类的历史流水仍可编辑。
        mockMvc.perform(patch("/api/v1/b/ledger/entries/" + jerseyEntryId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":1,"categoryId":%d,"amount":250.00,"version":0}
                                """.formatted(jerseyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(250.00))
                .andExpect(jsonPath("$.data.categoryName").value("球衣"));

        mockMvc.perform(get("/api/v1/c/ledger/categories").param("type", "1").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].name").value(
                        org.hamcrest.Matchers.hasItems("场地", "其他")))
                .andExpect(jsonPath("$.data.items[*].name").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("球衣"))));

        // LED-09 缺省含停用分类；显式 includeInactive=false 才排除。
        mockMvc.perform(get("/api/v1/b/ledger/categories").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.name == '球衣')].isActive")
                        .value(org.hamcrest.Matchers.contains(false)));
        mockMvc.perform(get("/api/v1/b/ledger/categories")
                        .param("includeInactive", "false").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].name").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("球衣"))));
        mockMvc.perform(get("/api/v1/b/ledger/categories")
                        .param("includeInactive", "true").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.name == '球衣')].isActive")
                        .value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.data.items[?(@.name == '球衣')].entryCount")
                        .value(org.hamcrest.Matchers.contains(1)));

        mockMvc.perform(get("/api/v1/c/ledger/entries").cookie(player))
                .andExpect(jsonPath("$.data.items[0].categoryName").value("球衣"))
                .andExpect(jsonPath("$.data.items[0].categoryDirection").value(1));

        // 已有方向不一致的流水时禁止改向；改为通用分类不破坏方向约束。
        patchCategory(admin, jerseyId, """
                {"direction":0}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        patchCategory(admin, jerseyId, """
                {"direction":null}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.direction").value((Object) null))
                .andExpect(jsonPath("$.data.directionLabel").value((Object) null));

        mockMvc.perform(patch("/api/v1/b/ledger/categories/" + jerseyId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"球衣2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("球衣2"));

        mockMvc.perform(get("/api/v1/c/ledger/entries").cookie(player))
                .andExpect(jsonPath("$.data.items[0].categoryName").value("球衣2"));

        createEntry(admin, entryJson(1, jerseyId, "10.00", "停用分类", OCCURRED_AT, null, null),
                idemKey()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(patch("/api/v1/b/ledger/categories/" + jerseyId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"球衣3","isActive":true,"unknown":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        createCategory(admin, """
                {"name":"%s"}
                """.formatted("分".repeat(33)))
                .andExpect(status().isBadRequest());
        createCategory(admin, """
                {"name":"非法方向","direction":2}
                """).andExpect(status().isBadRequest());

        Long emptyId = JsonPath.<Number>read(createCategory(admin, """
                        {"name":"空分类","direction":1}
                        """).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                "$.data.id").longValue();
        patchCategory(admin, emptyId, """
                {"direction":0}
                """).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.direction").value(0));
    }

    @Test
    void rejectsCrossTeamReferences() throws Exception {
        Cookie admin = bAdminLogin("13900004041");
        Long otherUser = insertUser("13900004042", "GUEST");
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position,
                                    secondary_positions, skill)
                VALUES (2, ?, '他队球员', 999, 1, 1, '[]', 3)
                """, otherUser);
        Long otherPlayerId = jdbc.queryForObject(
                "SELECT id FROM player WHERE user_id = ?", Long.class, otherUser);
        Long otherActivityId = insertActivity("他队活动", futureAt(20, 18), 0, 10,
                futureAt(20, 17), 2);
        jdbc.update("""
                INSERT INTO ledger_category (team_id, name, direction, sort_no)
                VALUES (2, '他队分类', 1, 1)
                """);
        Long otherCategoryId = jdbc.queryForObject(
                "SELECT id FROM ledger_category WHERE team_id = 2", Long.class);

        createEntry(admin, entryJson(0, categoryId("队费"), "10.00", "跨队球员", OCCURRED_AT,
                otherPlayerId, null), idemKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createEntry(admin, entryJson(0, categoryId("队费"), "10.00", "跨队活动", OCCURRED_AT,
                null, otherActivityId), idemKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createEntry(admin, entryJson(1, otherCategoryId, "10.00", "跨队分类", OCCURRED_AT,
                null, null), idemKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createEntry(admin, entryJson(0, categoryId("队费"), "10.00", "不存在", OCCURRED_AT,
                999999L, null), idemKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void summaryAndFilters() throws Exception {
        Cookie admin = bAdminLogin("13900004051");
        Cookie player = player("13900004052", "筛选球员", 903);
        Long playerId = playerId("13900004052");
        Long activityId = insertActivity("筛选活动", futureAt(20, 18), 0, 10, futureAt(20, 17), 1);
        Long membership = categoryId("队费");
        Long sponsor = categoryId("赞助");
        Long venue = categoryId("场地");
        Long equipment = categoryId("装备");
        Long dining = categoryId("餐饮");

        insertEntry(0, membership, "300.00", "11 月队费", "2026-11-20 20:10:00", playerId, activityId);
        insertEntry(0, sponsor, "200.00", "本地商家赞助", "2026-11-18 21:00:00", null, null);
        insertEntry(1, venue, "800.00", "11 月场地费", "2026-11-12 19:40:00", null, activityId);
        insertEntry(1, equipment, "240.00", "比赛用球", "2026-11-08 15:20:00", null, null);
        insertEntry(1, dining, "100.00", "赛后聚餐", "2026-11-05 12:00:00", playerId, null);

        mockMvc.perform(get("/api/v1/c/ledger/summary").cookie(player))
                .andExpect(jsonPath("$.data.income").value(500.00))
                .andExpect(jsonPath("$.data.expense").value(1140.00))
                .andExpect(jsonPath("$.data.balance").value(-640.00));

        mockMvc.perform(get("/api/v1/c/ledger/summary")
                        .param("from", "2026-11-15T00:00:00+08:00").cookie(player))
                .andExpect(jsonPath("$.data.income").value(500.00))
                .andExpect(jsonPath("$.data.expense").value(0.00))
                .andExpect(jsonPath("$.data.balance").value(500.00));

        mockMvc.perform(get("/api/v1/c/ledger/summary").param("type", "1").cookie(player))
                .andExpect(jsonPath("$.data.income").value(0.00))
                .andExpect(jsonPath("$.data.expense").value(1140.00))
                .andExpect(jsonPath("$.data.balance").value(-1140.00));

        mockMvc.perform(get("/api/v1/c/ledger/summary")
                        .param("categoryId", membership.toString()).cookie(player))
                .andExpect(jsonPath("$.data.income").value(300.00))
                .andExpect(jsonPath("$.data.expense").value(0.00));

        MvcResult bSummary = mockMvc.perform(get("/api/v1/b/ledger/summary")
                        .param("from", "2026-11-15T00:00:00+08:00")
                        .param("to", "2026-11-30T23:59:59+08:00").cookie(admin))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult cSummary = mockMvc.perform(get("/api/v1/c/ledger/summary")
                        .param("from", "2026-11-15T00:00:00+08:00")
                        .param("to", "2026-11-30T23:59:59+08:00").cookie(player))
                .andExpect(status().isOk())
                .andReturn();
        assertThat((Object) JsonPath.read(bSummary.getResponse().getContentAsString(), "$.data"))
                .isEqualTo(JsonPath.read(cSummary.getResponse().getContentAsString(), "$.data"));

        mockMvc.perform(get("/api/v1/c/ledger/entries").cookie(player))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.items[0].description").value("11 月队费"))
                .andExpect(jsonPath("$.data.items[4].description").value("赛后聚餐"));

        mockMvc.perform(get("/api/v1/c/ledger/entries")
                        .param("type", "1").param("keyword", "场地").cookie(player))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].description").value("11 月场地费"));

        mockMvc.perform(get("/api/v1/c/ledger/entries")
                        .param("playerId", playerId.toString()).cookie(player))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].description").value("11 月队费"))
                .andExpect(jsonPath("$.data.items[1].description").value("赛后聚餐"));

        mockMvc.perform(get("/api/v1/c/ledger/entries")
                        .param("activityId", activityId.toString()).cookie(player))
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/c/ledger/entries")
                        .param("from", "2026-11-10T00:00:00+08:00")
                        .param("to", "2026-11-19T23:59:59+08:00").cookie(player))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].description").value("本地商家赞助"))
                .andExpect(jsonPath("$.data.items[1].description").value("11 月场地费"));

        mockMvc.perform(get("/api/v1/c/ledger/entries")
                        .param("pageSize", "2").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/ledger/entries")
                        .param("type", "3").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/ledger/entries")
                        .param("from", "不是时间").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/ledger/entries")
                        .param("from", "2026-11-20T00:00:00+08:00")
                        .param("to", "2026-11-01T00:00:00+08:00").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/ledger/entries")
                        .param("categoryId", "0").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        for (int i = 0; i < 6; i++) {
            insertEntry(0, membership, "1.00", "分页" + i, "2026-10-0" + (i + 1) + " 10:00:00",
                    null, null);
        }
        mockMvc.perform(get("/api/v1/c/ledger/entries")
                        .param("page", "2").param("pageSize", "10").cookie(player))
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void versionConflictAndIdempotentReplay() throws Exception {
        Cookie admin = bAdminLogin("13900004061");
        Long categoryId = categoryId("队费");
        String body = entryJson(0, categoryId, "300.00", "幂等队费", OCCURRED_AT, null, null);
        String key = UUID.randomUUID().toString();

        Long entryId = JsonPath.<Number>read(createEntry(admin, body, key)
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.data.id").longValue();
        Long replayedId = JsonPath.<Number>read(createEntry(admin, body, key)
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.data.id").longValue();
        assertThat(replayedId).isEqualTo(entryId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team_ledger_entry",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'LEDGER_CREATE'
                """, Integer.class, entryId)).isEqualTo(1);

        createEntry(admin, entryJson(0, categoryId, "301.00", "换了金额", OCCURRED_AT, null, null),
                key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));

        mockMvc.perform(patch("/api/v1/b/ledger/entries/" + entryId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":350.00,"version":5}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(0));

        String patchKey = UUID.randomUUID().toString();
        mockMvc.perform(patch("/api/v1/b/ledger/entries/" + entryId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", patchKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":350.00,"description":"修改后","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(350.00))
                .andExpect(jsonPath("$.data.description").value("修改后"))
                .andExpect(jsonPath("$.data.version").value(1));

        MvcResult replayedPatch = mockMvc.perform(patch("/api/v1/b/ledger/entries/" + entryId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", patchKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":350.00,"description":"修改后","version":0}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(JsonPath.<Number>read(replayedPatch.getResponse().getContentAsString(),
                "$.data.version").intValue()).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/b/ledger/entries/" + entryId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(1));

        String deleteKey = UUID.randomUUID().toString();
        mockMvc.perform(delete("/api/v1/b/ledger/entries/" + entryId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", deleteKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
        assertThat(jdbc.queryForObject(
                "SELECT is_deleted FROM team_ledger_entry WHERE id = ?", Integer.class, entryId))
                .isEqualTo(1);
        mockMvc.perform(get("/api/v1/b/ledger/entries").cookie(admin))
                .andExpect(jsonPath("$.data.total").value(0));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'LEDGER_DELETE'
                """, Integer.class, entryId)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/b/ledger/entries/" + entryId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", deleteKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        mockMvc.perform(delete("/api/v1/b/ledger/entries/" + entryId)
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":2}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void permissionsFollowRoles() throws Exception {
        insertUser("13900004071", "GUEST");
        Cookie guest = cLogin("13900004071", "Passw0rd!");
        Cookie player = player("13900004072", "只读球员", 904);

        mockMvc.perform(get("/api/v1/c/ledger/entries"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        for (String path : List.of("/api/v1/c/ledger/entries", "/api/v1/c/ledger/summary",
                "/api/v1/c/ledger/categories")) {
            mockMvc.perform(get(path).cookie(guest))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40301));
            mockMvc.perform(get(path).cookie(player))
                    .andExpect(status().isOk());
        }

        Cookie playerBackend = bLoginExisting("13900004072");
        mockMvc.perform(get("/api/v1/b/ledger/entries").cookie(playerBackend))
                .andExpect(status().isOk());
        createEntry(playerBackend, entryJson(0, categoryId("队费"), "10.00", "球员写入",
                OCCURRED_AT, null, null), idemKey())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(get("/api/v1/b/ledger/categories").cookie(playerBackend))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private Cookie player(String phone, String name, int jersey) throws Exception {
        createPlayerUser(phone, name, jersey, 1);
        return cLogin(phone, "Passw0rd!");
    }

    private Cookie bLoginExisting(String phone) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode("Admin123!"), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Admin123!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }

    private Long categoryId(String name) {
        return jdbc.queryForObject(
                "SELECT id FROM ledger_category WHERE team_id = 1 AND name = ?", Long.class, name);
    }

    private String entryJson(int type, Long categoryId, String amount, String description,
                             String occurredAt, Long playerId, Long activityId) {
        return """
                {"type":%d,"categoryId":%d,"amount":%s,"description":"%s",
                 "occurredAt":"%s","playerId":%s,"activityId":%s}
                """.formatted(type, categoryId, amount, description, occurredAt,
                playerId == null ? "null" : playerId.toString(),
                activityId == null ? "null" : activityId.toString());
    }

    private ResultActions createEntry(Cookie admin, String body, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(post("/api/v1/b/ledger/entries")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions createCategory(Cookie admin, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/b/ledger/categories")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", idemKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patchCategory(Cookie admin, Long categoryId, String body)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/b/ledger/categories/" + categoryId)
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", idemKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Long insertEntry(int type, Long categoryId, String amount, String description,
                             String occurredAt, Long playerId, Long activityId) {
        jdbc.update("""
                INSERT INTO team_ledger_entry (team_id, type, category_id, amount, description,
                                               occurred_at, player_id, activity_id, version, created_by)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, 0, 1)
                """, type, categoryId, new BigDecimal(amount), description, occurredAt,
                playerId, activityId);
        return jdbc.queryForObject("SELECT MAX(id) FROM team_ledger_entry", Long.class);
    }
}
