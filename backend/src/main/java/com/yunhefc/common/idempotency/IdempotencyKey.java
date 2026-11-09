package com.yunhefc.common.idempotency;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("idempotency_key")
public class IdempotencyKey {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String endpoint;
    private String idemKey;
    private String requestHash;
    private Integer responseStatus;
    private String responseBody;
    private Instant createdAt;
    private Instant expiresAt;
    @TableLogic
    private Integer isDeleted;
}
