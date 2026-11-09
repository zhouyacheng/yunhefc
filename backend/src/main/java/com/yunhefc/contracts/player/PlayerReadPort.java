package com.yunhefc.contracts.player;

import java.util.Optional;

public interface PlayerReadPort {

    Optional<PlayerSnapshot> findByUserId(Long userId);
}
