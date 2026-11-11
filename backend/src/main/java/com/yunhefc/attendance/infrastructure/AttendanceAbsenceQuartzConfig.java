package com.yunhefc.attendance.infrastructure;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class AttendanceAbsenceQuartzConfig {

    private static final String EVERY_TEN_MINUTES = "0 */10 * * * ?";

    @Bean
    JobDetail attendanceAbsenceJobDetail() {
        return JobBuilder.newJob(AttendanceAbsenceJob.class)
                .withIdentity("attendanceAbsenceJob", "yunhefc")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger attendanceAbsenceTrigger(JobDetail attendanceAbsenceJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(attendanceAbsenceJobDetail)
                .withIdentity("attendanceAbsenceTrigger", "yunhefc")
                .withSchedule(CronScheduleBuilder.cronSchedule(EVERY_TEN_MINUTES)
                        .inTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
