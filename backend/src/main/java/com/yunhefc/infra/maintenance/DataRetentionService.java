package com.yunhefc.infra.maintenance;

import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.common.retention.RetentionTask;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 按 04 §2.3 的保留策略编排各模块清理任务；单类失败不阻断其余任务。
 */
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private static final int BATCH_SIZE = 1000;
    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);

    private final List<RetentionTask> tasks;

    public void cleanup() {
        cleanup(ShanghaiTime.now());
    }

    public void cleanup(LocalDateTime now) {
        for (RetentionTask task : tasks) {
            int deleted = 0;
            try {
                int batch;
                do {
                    batch = task.deleteBatch(now, BATCH_SIZE);
                    deleted += batch;
                } while (batch >= BATCH_SIZE);
                log.debug("retention cleanup done, task={}, deleted={}", task.name(), deleted);
            } catch (Exception e) {
                log.error("retention cleanup failed, task={}, deleted={}", task.name(), deleted, e);
            }
        }
    }
}
