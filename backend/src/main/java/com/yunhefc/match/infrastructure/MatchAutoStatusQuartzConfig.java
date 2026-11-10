package com.yunhefc.match.infrastructure;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class MatchAutoStatusQuartzConfig {

    private static final String EVERY_MINUTE = "0 * * * * ?";

    @Bean
    JobDetail matchAutoStatusJobDetail() {
        return JobBuilder.newJob(MatchAutoStatusJob.class)
                .withIdentity("matchAutoStatusJob", "yunhefc")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger matchAutoStatusTrigger(JobDetail matchAutoStatusJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(matchAutoStatusJobDetail)
                .withIdentity("matchAutoStatusTrigger", "yunhefc")
                .withSchedule(CronScheduleBuilder.cronSchedule(EVERY_MINUTE)
                        .inTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
