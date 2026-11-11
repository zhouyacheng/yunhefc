package com.yunhefc.lineup.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 阵型模板；预置模板 team_id 为空，自定义模板归属球队。删除为软删除，保留历史阵容引用。
 */
@Getter
@Setter
@TableName("formation_template")
public class FormationTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private String name;
    private Integer isPreset;
    private Long baseTemplateId;
    private Long createdBy;
    private Instant createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
    @TableLogic
    private Integer isDeleted;
}
