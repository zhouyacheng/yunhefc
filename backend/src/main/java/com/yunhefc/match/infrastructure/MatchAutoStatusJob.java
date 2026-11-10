package com.yunhefc.match.infrastructure;

import com.yunhefc.match.application.MatchAutoStatusApplicationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
public class MatchAutoStatusJob extends QuartzJobBean {

    private MatchAutoStatusApplicationService autoStatusService;

    @Autowired
    public void setAutoStatusService(MatchAutoStatusApplicationService autoStatusService) {
        this.autoStatusService = autoStatusService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        autoStatusService.advance();
    }
}
