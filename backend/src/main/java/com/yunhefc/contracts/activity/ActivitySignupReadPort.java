package com.yunhefc.contracts.activity;

import java.util.List;

/**
 * 活动报名只读端口；通知域按有效报名者投递取消通知。
 */
public interface ActivitySignupReadPort {

    /**
     * 活动有效报名（status=CONFIRMED）对应账号，按报名时间、ID 升序去重。
     */
    List<Long> findActiveUserIds(Long teamId, Long activityId);
}
