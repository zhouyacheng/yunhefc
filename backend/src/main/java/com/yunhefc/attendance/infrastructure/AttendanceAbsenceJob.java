package com.yunhefc.attendance.infrastructure;

import com.yunhefc.attendance.application.AttendanceAbsenceApplicationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
public class AttendanceAbsenceJob extends QuartzJobBean {

    private AttendanceAbsenceApplicationService absenceService;

    @Autowired
    public void setAbsenceService(AttendanceAbsenceApplicationService absenceService) {
        this.absenceService = absenceService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        absenceService.markDueAbsences();
    }
}
