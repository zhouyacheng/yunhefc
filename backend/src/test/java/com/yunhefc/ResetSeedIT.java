package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 每个 IT 方法前都会执行 release reset（reset.sql + V3 + V10 + V17 + V21）；
 * 这里直接断言重置后种子数据仍然完整，防止 reset.sql 或 RESET_DB 漏项。
 */
class ResetSeedIT extends IntegrationTestSupport {

    @Test
    void resetKeepsSeedData() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permission", Integer.class)).isEqualTo(37);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM role_permission", Integer.class)).isEqualTo(54);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_category WHERE is_deleted = 0", Integer.class))
                .isEqualTo(7);
    }
}
