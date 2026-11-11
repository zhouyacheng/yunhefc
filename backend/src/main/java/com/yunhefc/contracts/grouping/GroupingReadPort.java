package com.yunhefc.contracts.grouping;

import java.util.Optional;

public interface GroupingReadPort {

    /**
     * 本场已保存的分组快照；未生成、仅草稿或已清除返回空。
     */
    Optional<SavedGroupingSnapshot> findSavedByMatch(Long matchId);
}
