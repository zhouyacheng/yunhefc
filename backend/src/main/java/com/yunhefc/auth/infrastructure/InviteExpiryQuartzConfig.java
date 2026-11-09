package com.yunhefc.auth.infrastructure;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class InviteExpiryQuartzConfig {

    @Bean
    JobDetail inviteExpiryJobDetail() {
        return JobBuilder.newJob(InviteExpiryJob.class)
                .withIdentity("inviteExpiryJob", "yunhefc")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger inviteExpiryTrigger(JobDetail inviteExpiryJobDetail, InviteProperties properties) {
        return TriggerBuilder.newTrigger()
                .forJob(inviteExpiryJobDetail)
                .withIdentity("inviteExpiryTrigger", "yunhefc")
                .withSchedule(CronScheduleBuilder.cronSchedule(properties.getExpiryCron())
                        .inTimeZone(TimeZone.getTimeZone(properties.getZone()))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}
