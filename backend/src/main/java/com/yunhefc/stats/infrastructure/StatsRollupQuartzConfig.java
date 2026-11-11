package com.yunhefc.stats.infrastructure;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class StatsRollupQuartzConfig {

    private static final String EVERY_TEN_MINUTES = "0 */10 * * * ?";

    @Bean
    JobDetail statsRollupJobDetail() {
        return JobBuilder.newJob(StatsRollupJob.class)
                .withIdentity("statsRollupJob", "yunhefc")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger statsRollupTrigger(JobDetail statsRollupJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(statsRollupJobDetail)
                .withIdentity("statsRollupTrigger", "yunhefc")
                .withSchedule(CronScheduleBuilder.cronSchedule(EVERY_TEN_MINUTES)
                        .inTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
