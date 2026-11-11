package com.yunhefc.attendance.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.attendance.domain.Attendance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface AttendanceMapper extends BaseMapper<Attendance> {

    /**
     * 已结束且实际结束 +6h 已到的比赛；实际结束优先 status_updated_at，否则 end_at。
     */
    List<Long> selectAbsenceDueMatchIds(@Param("cutoff") Instant cutoff);

    /**
     * 给本场无考勤记录的 CONFIRMED 报名补记缺勤；NOT EXISTS 保证重复执行无副作用。
     */
    int insertSystemAbsences(@Param("matchId") Long matchId, @Param("recordedAt") Instant recordedAt);
}
