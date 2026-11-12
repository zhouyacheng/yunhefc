package com.yunhefc.common.util;

import java.security.SecureRandom;

/**
 * 26 位 ULID（大写 Crockford Base32），用于 OSS 对象键等不可枚举的标识。
 */
public final class Ulid {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LENGTH = 26;
    private static final int PADDING_BITS = LENGTH * 5 - 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    public static String generate() {
        byte[] bytes = new byte[16];
        long timestamp = System.currentTimeMillis();
        for (int i = 5; i >= 0; i--) {
            bytes[i] = (byte) (timestamp & 0xFF);
            timestamp >>>= 8;
        }
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        System.arraycopy(random, 0, bytes, 6, random.length);
        return encode(bytes);
    }

    static String encode(byte[] bytes) {
        char[] chars = new char[LENGTH];
        for (int charIndex = 0; charIndex < LENGTH; charIndex++) {
            int value = 0;
            for (int bit = 0; bit < 5; bit++) {
                value <<= 1;
                int position = charIndex * 5 + bit - PADDING_BITS;
                if (position >= 0) {
                    value |= (bytes[position / 8] >> (7 - position % 8)) & 1;
                }
            }
            chars[charIndex] = ALPHABET[value];
        }
        return new String(chars);
    }
}
