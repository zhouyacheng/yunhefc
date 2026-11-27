package com.yunhefc.content.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 队内动态：可引用 0 或 1 个相册；相册删除后动态保留，读取时相册信息为空。
 */
@Getter
@Setter
@TableName("content_post")
public class ContentPost {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long authorUserId;
    private String content;
    private Long albumId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
