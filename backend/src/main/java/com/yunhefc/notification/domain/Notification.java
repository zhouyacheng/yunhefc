package com.yunhefc.notification.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer type;
    private String title;
    private String body;
    private String refType;
    private Long refId;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer isDeleted;
}
