package com.yunhefc.auth.infrastructure;

import com.yunhefc.auth.application.InviteExpiryService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
public class InviteExpiryJob extends QuartzJobBean {

    private InviteExpiryService inviteExpiryService;

    @Autowired
    public void setInviteExpiryService(InviteExpiryService inviteExpiryService) {
        this.inviteExpiryService = inviteExpiryService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        inviteExpiryService.expire();
    }
}
