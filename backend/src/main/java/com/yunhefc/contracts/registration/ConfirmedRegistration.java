package com.yunhefc.contracts.registration;

/**
 * 本场已确认报名；分组候选按报名类型过滤外挂。
 */
public record ConfirmedRegistration(Long playerId, Integer registrationType) {
}
