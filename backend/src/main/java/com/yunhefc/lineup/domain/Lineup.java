package com.yunhefc.lineup.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 某场某一方的阵容版本；UK(match_id, side)，保存时按 version 乐观锁递增。
 */
@Getter
@Setter
@TableName("lineup")
public class Lineup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private Integer side;
    private Long formationTemplateId;
    private Integer version;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
    @TableLogic
    private Integer isDeleted;
}
