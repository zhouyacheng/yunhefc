package com.yunhefc.match.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_match")
public class TeamMatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private String title;
    private Integer type;
    private String format;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String venue;
    private Integer status;
    private String homeTeamName;
    private String awayTeamName;
    private Integer capacity;
    private Integer guestCapacity;
    private Integer waitlistCapacity;
    private Integer includeGuests;
    private Integer version;
    @TableLogic
    private Integer isDeleted;
}
