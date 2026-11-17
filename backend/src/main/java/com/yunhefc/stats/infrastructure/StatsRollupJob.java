package com.yunhefc.stats.infrastructure;

import com.yunhefc.stats.application.RollupResult;
import com.yunhefc.stats.application.StatsRollupApplicationService;

import java.util.List;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
public class StatsRollupJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(StatsRollupJob.class);

    private StatsRollupApplicationService rollupService;

    @Autowired
    public void setRollupService(StatsRollupApplicationService rollupService) {
        this.rollupService = rollupService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        try {
            List<RollupResult> results = rollupService.rollUpAllSeasons();
            int matches = results.stream().mapToInt(RollupResult::matchCount).sum();
            int players = results.stream().mapToInt(RollupResult::playerCount).sum();
            log.debug("stats rollup finished, teamId={}, seasons={}, matches={}, players={}",
                    results.isEmpty() ? null : results.get(0).teamId(), results.size(), matches, players);
        } catch (RuntimeException ex) {
            // 失败只告警不抛出：保留上一次汇总结果，下一轮继续全量重试。
            log.error("stats rollup failed, previous summary retained", ex);
        }
    }
}
