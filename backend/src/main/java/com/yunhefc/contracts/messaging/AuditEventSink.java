package com.yunhefc.contracts.messaging;

/**
 * 审计事件处理端口：由 audit 域实现，infra 分发器只依赖本接口。
 * {@code channel} 为投递通道标识，{@code partition}/{@code offset} 为进程内投递位点。
 */
public interface AuditEventSink {

    void apply(String message, String channel, int partition, long offset);
}
