package com.yunhefc.grouping.domain;

import com.yunhefc.common.security.Hashing;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 候选名单指纹：playerId 去重升序逗号连接后 SHA-256 小写十六进制。
 */
public final class RosterFingerprint {

    private RosterFingerprint() {
    }

    public static String of(Collection<Long> playerIds) {
        String joined = playerIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return Hashing.sha256(joined);
    }
}
