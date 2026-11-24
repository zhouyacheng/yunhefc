package com.yunhefc.contracts.activity;

import java.time.LocalDateTime;

/**
 * 跨模块读取的活动快照；只含基本类型，不含实体或 DTO。
 */
public record ActivitySnapshot(
        Long id,
        Long teamId,
        String title,
        LocalDateTime startAt) {
}
