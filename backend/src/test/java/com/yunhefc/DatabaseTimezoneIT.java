package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * RDS 默认会话时区可能为 +08:00；连接池必须显式固定 UTC，
 * 否则 DEFAULT CURRENT_TIMESTAMP(3) 与 ON UPDATE 会写入本地墙钟。
 */
class DatabaseTimezoneIT extends IntegrationTestSupport {

    @Test
    void forcesUtcSessionTimezone() {
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class))
                .isEqualTo("+00:00");
    }
}
