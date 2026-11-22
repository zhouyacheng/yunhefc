package com.yunhefc.auth.infrastructure;

import com.yunhefc.common.security.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 小程序绑手机一次性票据；Redis 键为 sha256(ticket)，值为用票据派生密钥加密的 openid。
 * 明文 openid 不落 Redis（java-rules §12）。
 */
@Component
@RequiredArgsConstructor
public class MiniBindTicketStore {

    static final Duration TTL = Duration.ofMinutes(10);

    private static final String KEY_PREFIX = "yunhefc:auth:mini-bind:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final StringRedisTemplate redis;

    public String save(String openid) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = ENCODER.encodeToString(bytes);
        redis.opsForValue().set(key(ticket), encrypt(ticket, openid), TTL);
        return ticket;
    }

    /** 单次消费；无效、已消费或已过期均返回 null。 */
    public String consume(String ticket) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 128) {
            return null;
        }
        String stored = redis.opsForValue().getAndDelete(key(ticket));
        return stored == null ? null : decrypt(ticket, stored);
    }

    private static String encrypt(String ticket, String openid) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(ticket), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] cipherText = cipher.doFinal(openid.getBytes(StandardCharsets.UTF_8));
            byte[] blob = new byte[nonce.length + cipherText.length];
            System.arraycopy(nonce, 0, blob, 0, nonce.length);
            System.arraycopy(cipherText, 0, blob, nonce.length, cipherText.length);
            return ENCODER.encodeToString(blob);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("小程序绑定票据加密失败", ex);
        }
    }

    private static String decrypt(String ticket, String stored) {
        try {
            byte[] blob = DECODER.decode(stored);
            if (blob.length <= NONCE_BYTES) {
                return null;
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(ticket),
                    new GCMParameterSpec(TAG_BITS, blob, 0, NONCE_BYTES));
            byte[] plain = cipher.doFinal(blob, NONCE_BYTES, blob.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return null;
        }
    }

    /** AES 密钥由票据派生：拿到 Redis 密文但没有票据无法还原 openid。 */
    private static SecretKeySpec secretKey(String ticket) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(ticket.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private String key(String ticket) {
        return KEY_PREFIX + Hashing.sha256(ticket);
    }
}
