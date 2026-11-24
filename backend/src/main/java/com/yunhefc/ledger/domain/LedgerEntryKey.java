package com.yunhefc.ledger.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 流水在球队内的复合标识；查询与写入必须同时带 teamId。
 */
@Getter
@RequiredArgsConstructor
public class LedgerEntryKey {

    private final Long entryId;
    private final Long teamId;
}
