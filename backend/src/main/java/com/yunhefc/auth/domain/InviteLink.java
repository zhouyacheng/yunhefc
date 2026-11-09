package com.yunhefc.auth.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("invite_link")
public class InviteLink {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private InviteType type;
    private String tokenHash;
    private Long matchId;
    private Long inviterUserId;
    private Instant expiresAt;
    private Integer maxUses;
    private Integer usedCount;
    private InviteStatus status;
    private Instant revokedAt;
    private Instant createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
    @TableLogic
    private Integer isDeleted;
}
