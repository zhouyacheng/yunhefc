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
@TableName("permission")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String resource;
    private String action;
    private String scope;
    private Instant createdAt;
    @TableLogic
    private Integer isDeleted;
}
