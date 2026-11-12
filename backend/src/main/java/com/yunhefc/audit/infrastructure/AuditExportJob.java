package com.yunhefc.audit.infrastructure;

import com.yunhefc.audit.application.AuditExportApplicationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
public class AuditExportJob extends QuartzJobBean {

    private AuditExportApplicationService auditExportApplicationService;

    @Autowired
    public void setAuditExportApplicationService(AuditExportApplicationService auditExportApplicationService) {
        this.auditExportApplicationService = auditExportApplicationService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        auditExportApplicationService.exportPreviousYear();
    }
}
