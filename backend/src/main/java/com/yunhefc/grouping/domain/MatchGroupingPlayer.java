package com.yunhefc.grouping.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 分组成员行；生成时按分组整体物理替换。
 */
@Getter
@Setter
@TableName("match_grouping_player")
public class MatchGroupingPlayer {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupingId;
    private Long playerId;
    private Integer team;
    private BigDecimal manualScore;
    private BigDecimal autoScore;
    private BigDecimal finalScore;
    private Instant createdAt;
    private Integer isDeleted;
}
