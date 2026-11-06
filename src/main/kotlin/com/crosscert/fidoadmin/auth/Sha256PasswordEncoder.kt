package com.crosscert.fidoadmin.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.HexFormat
import java.util.Locale
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * 기존 CCFA_MANAGER.USER_PW 형식(salt 없는 SHA-256 hex 64자)과 호환되는 인코더.
 * 저장 방식 강화(salt/bcrypt)는 다른 시스템과의 호환 문제로 범위 밖이다.
 */
class Sha256PasswordEncoder : PasswordEncoder {

    /** null 이면 Java 시절과 같이 NullPointerException 이다. */
    override fun encode(rawPassword: CharSequence?): String = sha256Hex(rawPassword!!.toString())

    override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isJavaBlank()) {
            return false
        }
        // 상수 시간 비교. 저장값은 대소문자가 섞여 있을 수 있어 소문자로 맞춘 뒤 비교한다(결과는 equalsIgnoreCase 와 같다).
        val actual = encode(rawPassword).toByteArray(StandardCharsets.US_ASCII)
        val stored = encodedPassword.javaTrim().lowercase(Locale.ROOT).toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(actual, stored)
    }

    companion object {
        @JvmStatic
        fun sha256Hex(input: String): String {
            try {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
                return HexFormat.of().formatHex(digest)
            } catch (e: NoSuchAlgorithmException) {
                throw IllegalStateException("SHA-256 not available", e)
            }
        }
    }
}
