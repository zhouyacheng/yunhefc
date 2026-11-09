package com.yunhefc.match.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("team_match")
public class TeamMatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private String title;
    private MatchType type;
    private String format;
    private Instant startAt;
    private Instant endAt;
    private String venue;
    private MatchStatus status;
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
