package com.yunhefc.grouping.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 队内分组主行；按 04 例外采用覆盖写与物理删除，不使用 @TableLogic。
 */
@Getter
@Setter
@TableName("match_grouping")
public class MatchGrouping {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private BigDecimal manualWeight;
    private BigDecimal autoWeight;
    private String rosterFingerprint;
    private Instant generatedAt;
    private Instant savedAt;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer isDeleted;
}
