package com.yunhefc.event.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("match_event")
public class MatchEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private EventTeam team;
    private EventType type;
    private Long playerId;
    private Long assistPlayerId;
    private Long inPlayerId;
    private Long outPlayerId;
    private Integer minute;
    private EventHalf half;
    private Boolean penalty;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;
    private Instant deletedAt;
    private Long deletedBy;
    private Integer version;
    @TableLogic
    private Integer isDeleted;
}
