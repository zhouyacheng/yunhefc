package com.yunhefc.infra.outbox;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
public class OutboxRelayJob extends QuartzJobBean {

    private OutboxRelay outboxRelay;

    @Autowired
    public void setOutboxRelay(OutboxRelay outboxRelay) {
        this.outboxRelay = outboxRelay;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        outboxRelay.relay();
    }
}
