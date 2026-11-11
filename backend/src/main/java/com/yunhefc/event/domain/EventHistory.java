package com.yunhefc.event.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("event_history")
public class EventHistory {

    public static final int CREATE = 0;
    public static final int UPDATE = 1;
    public static final int DELETE = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private Integer action;
    private String beforeJson;
    private String afterJson;
    private Long actorUserId;
    private String requestId;
    private Instant createdAt;
    @TableLogic
    private Integer isDeleted;
}
