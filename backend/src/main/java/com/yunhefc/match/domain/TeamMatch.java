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
    private Instant signupDeadline;
    private Instant cancelLock;
    private String venue;
    private String competition;
    private String season;
    private Integer round;
    private String homeTeamName;
    private String awayTeamName;
    private OurSide ourSide;
    private MatchStatus status;
    private Boolean manualStatus;
    private Integer capacity;
    private Integer guestCapacity;
    private Integer waitlistCapacity;
    private Boolean includeGuests;
    private Integer scoreHome;
    private Integer scoreAway;
    private Integer seasonMatchNo;
    private Integer totalMatchNo;
    private Integer version;
    private Instant statusUpdatedAt;
    private Long statusUpdatedBy;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    @TableLogic
    private Integer isDeleted;
}
