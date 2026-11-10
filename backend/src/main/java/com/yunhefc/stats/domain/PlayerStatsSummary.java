package com.yunhefc.stats.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("player_stats_summary")
public class PlayerStatsSummary {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long playerId;
    private String season;
    private Integer appearances;
    private Integer goals;
    private Integer assists;
    private Instant createdAt;
    private Instant updatedAt;
    @TableLogic
    private Integer isDeleted;
}
