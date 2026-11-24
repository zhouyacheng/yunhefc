package com.yunhefc.contracts.activity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 活动只读端口；账本校验关联活动归属并读取展示标题。
 */
public interface ActivityReadPort {

    /**
     * 按球队读取活动；不存在、不属于该球队或已删除返回空。
     */
    Optional<ActivitySnapshot> findById(Long teamId, Long activityId);

    /**
     * 批量读取球队活动；不存在的 ID 不返回键。
     */
    Map<Long, ActivitySnapshot> findByIds(Long teamId, Collection<Long> activityIds);
}
