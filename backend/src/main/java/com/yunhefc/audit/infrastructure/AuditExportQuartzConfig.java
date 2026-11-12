package com.yunhefc.audit.infrastructure;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
@EnableConfigurationProperties(AuditExportProperties.class)
public class AuditExportQuartzConfig {

    @Bean
    JobDetail auditExportJobDetail() {
        return JobBuilder.newJob(AuditExportJob.class)
                .withIdentity("auditExportJob", "yunhefc")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger auditExportTrigger(JobDetail auditExportJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(auditExportJobDetail)
                .withIdentity("auditExportTrigger", "yunhefc")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 0 1 1 ?")
                        .inTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                        // 年度冷备错过整点后应尽快补跑一次，而不是跳过整整一年。
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
