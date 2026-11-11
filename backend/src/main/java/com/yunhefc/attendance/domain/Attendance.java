package com.yunhefc.attendance.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@TableName("attendance")
public class Attendance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private Long playerId;
    private AttendanceStatus status;
    private AttendanceSource source;
    private Long recordedBy;
    private Instant recordedAt;
    @TableLogic
    private Integer isDeleted;
}
