package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.common.time.ShanghaiTime;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * RDS 与本地容器默认会话时区为 +08:00；连接池必须显式固定同一时区，
 * 否则 DEFAULT CURRENT_TIMESTAMP(3) 与 ON UPDATE 会与服务端默认值错位。
 */
class DatabaseTimezoneIT extends IntegrationTestSupport {

    @Test
    void forcesShanghaiSessionTimezone() {
        assertThat(jdbc.queryForObject("SELECT @@global.time_zone", String.class))
                .isEqualTo("+08:00");
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class))
                .isEqualTo("+08:00");
    }

    @Test
    void sessionClockUsesShanghaiZone() {
        LocalDateTime dbNow = jdbc.queryForObject("SELECT NOW(3)", LocalDateTime.class);
        assertThat(Duration.between(dbNow, ShanghaiTime.now()).abs())
                .isLessThan(Duration.ofSeconds(5));
    }
}
