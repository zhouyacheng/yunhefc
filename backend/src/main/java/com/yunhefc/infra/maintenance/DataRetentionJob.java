package com.yunhefc.infra.maintenance;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
public class DataRetentionJob extends QuartzJobBean {

    private DataRetentionService dataRetentionService;

    @Autowired
    public void setDataRetentionService(DataRetentionService dataRetentionService) {
        this.dataRetentionService = dataRetentionService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        dataRetentionService.cleanup();
    }
}
