package com.yunhefc.contracts.messaging;

/**
 * 通知事件处理端口：由 notification 域实现，infra 分发器只依赖本接口。
 * {@code channel} 为投递通道标识，{@code partition}/{@code offset} 为进程内投递位点。
 */
public interface NotificationEventSink {

    void apply(String message, String channel, int partition, long offset);
}
