package com.yunhefc.attendance.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.attendance.domain.Attendance;
import com.yunhefc.attendance.infrastructure.AttendanceMapper;
import com.yunhefc.contracts.attendance.AttendanceReadPort;
import com.yunhefc.contracts.attendance.AttendanceSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AttendanceReadAdapter implements AttendanceReadPort {

    private final AttendanceMapper attendanceMapper;

    @Override
    public Optional<AttendanceSnapshot> findByMatchAndPlayer(Long matchId, Long playerId) {
        return Optional.ofNullable(attendanceMapper.selectOne(new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getMatchId, matchId)
                        .eq(Attendance::getPlayerId, playerId)
                        .last("LIMIT 1")))
                .map(AttendanceReadAdapter::toSnapshot);
    }

    private static AttendanceSnapshot toSnapshot(Attendance attendance) {
        return new AttendanceSnapshot(
                attendance.getId(),
                attendance.getPlayerId(),
                attendance.getStatus().code(),
                attendance.getStatus().label(),
                attendance.getSource().code(),
                attendance.getSource().label(),
                attendance.getRecordedAt());
    }
}
