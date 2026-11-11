package com.yunhefc.stats.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.stats.domain.PlayerStatsSummary;
import com.yunhefc.stats.infrastructure.PlayerStatsSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 汇总表只读访问；事务边界收在数据库查询内，缓存读写由调用方在事务外完成。
 */
@Component
@RequiredArgsConstructor
public class StatsSummaryReader {

    private final PlayerStatsSummaryMapper statsMapper;

    @Transactional(readOnly = true)
    public List<PlayerStatsRow> load(Long teamId, String season) {
        return statsMapper.selectList(new LambdaQueryWrapper<PlayerStatsSummary>()
                        .eq(PlayerStatsSummary::getTeamId, teamId)
                        .eq(PlayerStatsSummary::getSeason, season))
                .stream()
                .map(summary -> PlayerStatsRow.of(summary, summary.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> playerIds(Long teamId, String season) {
        return statsMapper.selectPlayerIds(teamId, season);
    }
}
