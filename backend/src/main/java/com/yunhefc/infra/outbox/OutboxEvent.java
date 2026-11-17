package com.yunhefc.infra.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("outbox_event")
public class OutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long teamId;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private String payloadJson;
    private Integer status;
    private Integer attempts;
    private LocalDateTime nextRetryAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    @TableLogic
    private Integer isDeleted;
}
