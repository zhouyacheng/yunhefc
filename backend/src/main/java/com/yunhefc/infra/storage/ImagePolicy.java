package com.yunhefc.infra.storage;

/**
 * 图片上传策略：对象键前缀、体积上限与超限文案由调用方决定，ImageStore 只消费。
 */
public record ImagePolicy(String prefix, long maxBytes, String tooLargeMessage) {
}
