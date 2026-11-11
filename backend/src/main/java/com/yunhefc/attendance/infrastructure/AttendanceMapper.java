package com.yunhefc.attendance.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.attendance.domain.Attendance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttendanceMapper extends BaseMapper<Attendance> {
}
