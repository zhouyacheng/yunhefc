package com.yunhefc.contracts.match;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchReadPort {

    Optional<MatchSnapshot> findById(Long matchId);

    List<MatchSnapshot> findByIds(Collection<Long> matchIds);
}
