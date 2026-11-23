package com.yunhefc.contracts.grouping;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface GroupingReadPort {

    /**
     * 本场已保存的分组快照；未生成、仅草稿或已清除返回空。
     */
    Optional<SavedGroupingSnapshot> findSavedByMatch(Long matchId);

    /**
     * 批量读取已保存分组；未生成、仅草稿或已清除的比赛不返回键。
     */
    Map<Long, SavedGroupingSnapshot> findSavedByMatchIds(Collection<Long> matchIds);
}
