package com.yunhefc.player.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.player.Position;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("player")
public class Player {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long userId;
    private String name;
    private Integer jersey;
    private PlayerType type;
    private Position position;
    private String secondaryPositions;
    private String region;
    private String avatarUrl;
    private Integer skill;
    private Instant createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
    @TableLogic
    private Integer isDeleted;
}
