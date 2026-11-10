package com.crosscert.fidoadmin.common

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class DbConfigCrypto(base64Key: String?) {

    private val key: SecretKeySpec?
    private val random = SecureRandom()

    init {
        // 모든 설정이 평문이면 키 없이도 사용할 수 있습니다.
        if (base64Key == null || base64Key.javaIsBlank()) {
            key = null
        } else {
            val decoded: ByteArray = try {
                Base64.getDecoder().decode(base64Key.javaTrim())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "DB_CONFIG_KEY는 Base64 형식이어야 합니다.")
            }

            if (decoded.size != 32) {
                throw IllegalArgumentException(
                    "DB_CONFIG_KEY는 Base64 디코딩 후 32바이트여야 합니다.")
            }

            key = SecretKeySpec(decoded, ALGORITHM)
        }
    }

    fun encrypt(plainText: String?): String {
        requireKey()

        if (plainText == null) {
            throw IllegalArgumentException("암호화할 값이 없습니다.")
        }

        try {
            val iv = ByteArray(IV_LENGTH)
            random.nextBytes(iv)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, iv)
            )

            val encrypted = cipher.doFinal(
                plainText.toByteArray(StandardCharsets.UTF_8)
            )

            val payload = ByteBuffer
                .allocate(iv.size + encrypted.size)
                .put(iv)
                .put(encrypted)
                .array()

            return (PREFIX
                + Base64.getEncoder().encodeToString(payload)
                + ")")
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("DB 설정 암호화에 실패했습니다.", e)
        }
    }

    fun decryptIfEncrypted(value: String?): String? {
        if (value == null) {
            return null
        }

        if (value.startsWith(PLAIN_PREFIX)) {
            return value.substring(PLAIN_PREFIX.length)
        }

        if (!value.startsWith(PREFIX)) {
            return value
        }

        if (!value.endsWith(")")) {
            throw IllegalArgumentException("DB 암호문 형식이 올바르지 않습니다.")
        }

        requireKey()

        try {
            val encoded = value.substring(
                PREFIX.length, value.length - 1
            )
            val payload = Base64.getDecoder().decode(encoded)

            if (payload.size < IV_LENGTH + TAG_BITS / 8) {
                throw IllegalArgumentException("암호문 길이가 부족합니다.")
            }

            val buffer = ByteBuffer.wrap(payload)

            val iv = ByteArray(IV_LENGTH)
            buffer.get(iv)

            val encrypted = ByteArray(buffer.remaining())
            buffer.get(encrypted)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, iv)
            )

            return String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8
            )
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException(
                "DB 설정 복호화 실패: 키 또는 암호문을 확인하세요.", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(
                "DB 설정 복호화 실패: 키 또는 암호문을 확인하세요.", e)
        }
    }

    private fun requireKey() {
        if (key == null) {
            throw IllegalStateException("DB_CONFIG_KEY 설정이 필요합니다.")
        }
    }

    private companion object {
        private const val PREFIX = "ENC("
        private const val PLAIN_PREFIX = "[plain]"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
