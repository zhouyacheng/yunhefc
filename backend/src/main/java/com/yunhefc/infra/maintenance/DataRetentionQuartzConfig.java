package com.yunhefc.infra.maintenance;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class DataRetentionQuartzConfig {

    @Bean
    JobDetail dataRetentionJobDetail() {
        return JobBuilder.newJob(DataRetentionJob.class)
                .withIdentity("dataRetentionJob", "yunhefc")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger dataRetentionTrigger(JobDetail dataRetentionJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(dataRetentionJobDetail)
                .withIdentity("dataRetentionTrigger", "yunhefc")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 30 3 * * ?")
                        .inTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
