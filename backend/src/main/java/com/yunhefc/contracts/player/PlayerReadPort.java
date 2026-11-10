package com.yunhefc.contracts.player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerReadPort {

    Optional<PlayerSnapshot> findByUserId(Long userId);

    List<PlayerSnapshot> findByUserIds(Collection<Long> userIds);
}
