package com.yunhefc.contracts.player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerReadPort {

    Optional<PlayerSnapshot> findById(Long playerId);

    Optional<PlayerSnapshot> findByUserId(Long userId);

    List<PlayerSnapshot> findByUserIds(Collection<Long> userIds);

    /**
     * 球队页公开名单：正式球员且账号 ACTIVE。
     */
    List<PlayerPublicSnapshot> findPublicRoster(Long teamId);
}
