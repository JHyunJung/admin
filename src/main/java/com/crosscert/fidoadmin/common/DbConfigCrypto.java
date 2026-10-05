package com.crosscert.fidoadmin.common;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class DbConfigCrypto {

    private static final String PREFIX = "ENC(";
    private static final String PLAIN_PREFIX = "[plain]";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public DbConfigCrypto(String base64Key) {
        // 모든 설정이 평문이면 키 없이도 사용할 수 있습니다.
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "DB_CONFIG_KEY는 Base64 형식이어야 합니다.");
        }

        if (decoded.length != 32) {
            throw new IllegalArgumentException(
                    "DB_CONFIG_KEY는 Base64 디코딩 후 32바이트여야 합니다.");
        }

        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plainText) {
        requireKey();

        if (plainText == null) {
            throw new IllegalArgumentException("암호화할 값이 없습니다.");
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_BITS, iv)
            );

            byte[] encrypted = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            byte[] payload = ByteBuffer
                    .allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();

            return PREFIX
                    + Base64.getEncoder().encodeToString(payload)
                    + ")";
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("DB 설정 암호화에 실패했습니다.", e);
        }
    }

    public String decryptIfEncrypted(String value) {
        if (value == null) {
            return null;
        }

        if (value.startsWith(PLAIN_PREFIX)) {
            return value.substring(PLAIN_PREFIX.length());
        }

        if (!value.startsWith(PREFIX)) {
            return value;
        }

        if (!value.endsWith(")")) {
            throw new IllegalArgumentException("DB 암호문 형식이 올바르지 않습니다.");
        }

        requireKey();

        try {
            String encoded = value.substring(
                    PREFIX.length(), value.length() - 1
            );
            byte[] payload = Base64.getDecoder().decode(encoded);

            if (payload.length < IV_LENGTH + TAG_BITS / 8) {
                throw new IllegalArgumentException("암호문 길이가 부족합니다.");
            }

            ByteBuffer buffer = ByteBuffer.wrap(payload);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_BITS, iv)
            );

            return new String(
                    cipher.doFinal(encrypted),
                    StandardCharsets.UTF_8
            );
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "DB 설정 복호화 실패: 키 또는 암호문을 확인하세요.", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("DB_CONFIG_KEY 설정이 필요합니다.");
        }
    }
}
