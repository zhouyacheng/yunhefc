package com.yunhefc.contracts.match;

import java.util.Optional;

public interface MatchReadPort {

    Optional<MatchSnapshot> findById(Long matchId);
}
