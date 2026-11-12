package com.yunhefc.contracts.player;

/**
 * 跨模块读取的球员档案快照。
 */
public record PlayerSnapshot(
        Long id,
        Long teamId,
        Long userId,
        String name,
        Integer jersey,
        Integer type,
        Integer position,
        Integer skill,
        // 私有 OSS 原始对象键；读响应由调用方现签 URL。
        String avatarUrl) {
}
