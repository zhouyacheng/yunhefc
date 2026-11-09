package com.yunhefc.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long actorUserId;
    private String actorName;
    private String action;
    private String targetType;
    private Long targetId;
    private String beforeJson;
    private String afterJson;
    private String requestId;
    private String ip;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer isDeleted;
}
