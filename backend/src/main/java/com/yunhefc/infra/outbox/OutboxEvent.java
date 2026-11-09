package com.yunhefc.infra.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

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
    private Instant nextRetryAt;
    private String leaseOwner;
    private Instant leaseUntil;
    private String lastError;
    private Instant createdAt;
    private Instant sentAt;
    @TableLogic
    private Integer isDeleted;
}
