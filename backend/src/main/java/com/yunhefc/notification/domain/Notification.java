package com.yunhefc.notification.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private NotificationType type;
    private String title;
    private String body;
    private String refType;
    private Long refId;
    private Instant readAt;
    private Instant createdAt;
    @TableLogic
    private Integer isDeleted;
}
