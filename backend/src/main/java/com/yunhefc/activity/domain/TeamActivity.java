package com.yunhefc.activity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("team_activity")
public class TeamActivity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private String title;
    private ActivityType type;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime signupDeadline;
    private Integer capacity;
    private BigDecimal costPerPerson;
    private String location;
    private String locationImageKey;
    private String description;
    private ActivityStatus status;
    private String cancelReason;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
