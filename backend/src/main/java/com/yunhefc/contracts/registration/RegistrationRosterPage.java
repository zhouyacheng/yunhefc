package com.yunhefc.contracts.registration;

import java.util.List;

/**
 * 比赛报名名单分页结果；items 按报名时间、ID 升序。
 */
public record RegistrationRosterPage(List<RegistrationSnapshot> items, long total) {
}
