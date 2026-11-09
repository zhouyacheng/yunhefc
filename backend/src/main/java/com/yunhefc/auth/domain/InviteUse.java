package com.yunhefc.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("invite_use")
public class InviteUse {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inviteId;
    private Long userId;
    private Instant usedAt;
    @TableLogic
    private Integer isDeleted;
}
