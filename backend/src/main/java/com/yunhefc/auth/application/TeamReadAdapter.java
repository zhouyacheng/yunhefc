package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.auth.domain.Team;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.team.TeamReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TeamReadAdapter implements TeamReadPort {

    private final TeamMapper teamMapper;

    @Override
    public Long currentTeamId() {
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>()
                .orderByAsc(Team::getId)
                .last("LIMIT 1"));
        if (team == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "球队未初始化");
        }
        return team.getId();
    }
}
