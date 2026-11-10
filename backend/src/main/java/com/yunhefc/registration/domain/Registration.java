package com.yunhefc.registration.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("registration")
public class Registration {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private Long playerId;
    private RegistrationType type;
    private RegistrationStatus status;
    private Instant registeredAt;
    private Instant cancelledAt;
    private Long cancelledBy;
    private Instant updatedAt;
    @TableLogic
    private Integer isDeleted;
}
