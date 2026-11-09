package com.yunhefc.infra.outbox;

import com.yunhefc.common.config.OutboxProperties;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "yunhefc.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayQuartzConfig {

    @Bean
    JobDetail outboxRelayJobDetail() {
        return JobBuilder.newJob(OutboxRelayJob.class)
                .withIdentity("outboxRelayJob", "yunhefc")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger outboxRelayTrigger(JobDetail outboxRelayJobDetail, OutboxProperties properties) {
        return TriggerBuilder.newTrigger()
                .forJob(outboxRelayJobDetail)
                .withIdentity("outboxRelayTrigger", "yunhefc")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(properties.getRelayIntervalMs())
                        .repeatForever()
                        .withMisfireHandlingInstructionNextWithRemainingCount())
                .startNow()
                .build();
    }
}
