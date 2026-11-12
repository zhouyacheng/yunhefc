package com.yunhefc;

import java.nio.charset.StandardCharsets;

/**
 * 头像测试用的最小合法文件头与字节拼接工具。
 */
public final class AvatarFixtures {

    public static final byte[] JPEG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F',
            0x00, 0x01, (byte) 0xFF, (byte) 0xD9
    };

    public static final byte[] PNG = concat(
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
            new byte[] {0x00, 0x00, 0x00, 0x0D},
            "IHDR".getBytes(StandardCharsets.US_ASCII),
            new byte[] {0x00, 0x00, 0x00, 0x00},
            "IEND".getBytes(StandardCharsets.US_ASCII));

    public static final byte[] WEBP = concat(
            "RIFF".getBytes(StandardCharsets.US_ASCII),
            new byte[] {0x1A, 0x00, 0x00, 0x00},
            "WEBP".getBytes(StandardCharsets.US_ASCII),
            "VP8 ".getBytes(StandardCharsets.US_ASCII),
            new byte[] {0x00, 0x00, 0x00, 0x00});

    private AvatarFixtures() {
    }

    public static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
