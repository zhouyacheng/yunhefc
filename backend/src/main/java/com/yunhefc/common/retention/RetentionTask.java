package com.yunhefc.common.retention;

import java.time.Instant;

/**
 * 数据保留清理端口：各模块实现单批物理删除，由统一保留任务循环到不足一批。
 */
public interface RetentionTask {

    String name();

    /**
     * 物理删除最多 {@code limit} 条已过保留期的数据，返回实际删除行数。
     */
    int deleteBatch(Instant now, int limit);
}
