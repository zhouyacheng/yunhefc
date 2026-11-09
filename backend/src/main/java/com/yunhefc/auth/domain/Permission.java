package com.yunhefc.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("permission")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String resource;
    private String action;
    private String scope;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer isDeleted;
}
