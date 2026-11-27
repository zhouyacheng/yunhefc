package com.yunhefc.infra.outbox;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class OutboxDispatcherConfig {

    public static final String EXECUTOR = "outboxDispatcherExecutor";

    /**
     * 单批最多 20 条事件；4/8 线程 + 队列 32 足够吸收，且不挤占 Web 请求的数据库连接。
     * 队列满时回落到 Quartz 中继线程同步执行，形成背压而不丢事件。
     */
    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor outboxDispatcherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("outbox-dispatch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
