package com.yunhefc.contracts.player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerReadPort {

    Optional<PlayerSnapshot> findById(Long playerId);

    Optional<PlayerSnapshot> findByUserId(Long userId);

    /**
     * 按球队批量读取球员；不属于该球队或不存在的 ID 不返回。
     */
    List<PlayerSnapshot> findByIds(Long teamId, Collection<Long> playerIds);

    List<PlayerSnapshot> findByUserIds(Collection<Long> userIds);

    /**
     * 按姓名模糊匹配读取用户 ID；关键词按字面量匹配。
     */
    List<Long> findUserIdsByNameContaining(String keyword);

    /**
     * 球队页公开名单：正式球员且账号 ACTIVE。
     */
    List<PlayerPublicSnapshot> findPublicRoster(Long teamId);
}
