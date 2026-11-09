package com.yunhefc.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("invite_link")
public class InviteLink {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Integer type;
    private String tokenHash;
    private Long matchId;
    private Long inviterUserId;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer usedCount;
    private Integer status;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
