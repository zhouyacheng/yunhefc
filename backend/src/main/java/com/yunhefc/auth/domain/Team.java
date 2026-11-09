package com.yunhefc.auth.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("team")
public class Team {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String shortName;
    private Instant createdAt;
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
    @TableLogic
    private Integer isDeleted;
}
