package com.yunhefc.grouping.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private LocalDateTime generatedAt;
    private LocalDateTime savedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer isDeleted;
}
