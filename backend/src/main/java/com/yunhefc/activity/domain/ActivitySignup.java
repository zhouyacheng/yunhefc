package com.yunhefc.activity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 同一活动同一球员仅一行（uk_activity_signup）；取消与重新报名复用该行，不保留历史行。
 */
@Getter
@Setter
@TableName("activity_signup")
public class ActivitySignup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long playerId;
    private Boolean withFamily;
    private String familyNote;
    private SignupStatus status;
    private LocalDateTime signedUpAt;
    private LocalDateTime cancelledAt;
    private Long cancelledBy;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
