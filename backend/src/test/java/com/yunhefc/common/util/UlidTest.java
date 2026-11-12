package com.yunhefc.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

class UlidTest {

    @Test
    void generatesTwentySixUppercaseCrockfordCharacters() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String value = Ulid.generate();
            assertThat(value).matches("[0-9A-HJKMNP-TV-Z]{26}");
            values.add(value);
        }
        assertThat(values).hasSize(1000);
    }

    @Test
    void encodesBoundaryValuesWithTwoPaddingBits() {
        assertThat(Ulid.encode(new byte[16])).isEqualTo("00000000000000000000000000");
        assertThat(Ulid.encode(fullySetBytes())).isEqualTo("7ZZZZZZZZZZZZZZZZZZZZZZZZZ");
    }

    private byte[] fullySetBytes() {
        byte[] bytes = new byte[16];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) 0xFF;
        }
        return bytes;
    }
}
