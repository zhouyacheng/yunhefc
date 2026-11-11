package com.yunhefc.grouping.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RosterFingerprintTest {

    @Test
    void sortsAndDeduplicatesBeforeHashing() {
        assertThat(RosterFingerprint.of(List.of(3L, 1L, 2L, 1L)))
                .isEqualTo("8a6ae15122001229edb8866f56e342af12ae8187203c3e3b33931743e7c0c48d");
    }

    @Test
    void emptyRosterUsesTheEmptyStringHash() {
        assertThat(RosterFingerprint.of(List.of()))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void producesLowercaseSixtyFourCharacterHex() {
        assertThat(RosterFingerprint.of(List.of(10L, 20L)))
                .matches("[0-9a-f]{64}");
    }
}
