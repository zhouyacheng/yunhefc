package com.yunhefc.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 业务时间统一为 Asia/Shanghai 墙钟；取当前时间必须带该时区，避免依赖 JVM 默认时区。
 */
public final class ShanghaiTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private ShanghaiTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static String formatIso(LocalDateTime value) {
        return value.atOffset(OFFSET).format(ISO_OFFSET);
    }

    /** 接受带 Z/偏移的 ISO-8601，也接受本地时间；统一转换为 Asia/Shanghai 墙钟。 */
    public static LocalDateTime parseLenient(String text) {
        String value = text.trim();
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value);
        }
    }
}
