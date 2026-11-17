package com.yunhefc.stats.application;

import com.yunhefc.stats.domain.PlayerStatsSummary;
import com.yunhefc.stats.infrastructure.PlayerStatsSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 汇总表批量覆盖写；事务在此提交，缓存刷新由调用方在提交后完成。
 */
@Component
@RequiredArgsConstructor
public class StatsSummaryWriter {

    private final PlayerStatsSummaryMapper statsMapper;

    @Transactional
    public void replace(List<PlayerStatsSummary> summaries, LocalDateTime now) {
        if (!summaries.isEmpty()) {
            statsMapper.upsertBatch(summaries, now);
        }
    }
}
