package com.crosscert.fidoadmin.common

import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DbConfigCryptoTest {

    @Test
    fun 암호화한_값을_같은_키로_복호화한다() {
        val crypto = DbConfigCrypto(KEY)
        val enc = crypto.encrypt("jdbc:oracle:thin:@db:1521/한글")

        assertThat(enc).startsWith("ENC(").endsWith(")")
        assertThat(crypto.decryptIfEncrypted(enc)).isEqualTo("jdbc:oracle:thin:@db:1521/한글")
    }

    @Test
    fun 평문과_plain_접두어는_키_없이_그대로_돌려준다() {
        val crypto = DbConfigCrypto(null)

        assertThat(crypto.decryptIfEncrypted("kbfido")).isEqualTo("kbfido")
        assertThat(crypto.decryptIfEncrypted("[plain]ENC(raw)")).isEqualTo("ENC(raw)")
        assertThat(crypto.decryptIfEncrypted(null)).isNull()
    }

    @Test
    fun 암호문인데_키가_없으면_실패한다() {
        val enc = DbConfigCrypto(KEY).encrypt("secret")

        assertThatThrownBy { DbConfigCrypto("").decryptIfEncrypted(enc) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("DB_CONFIG_KEY")
    }

    @Test
    fun 다른_키로는_복호화하지_못한다() {
        val enc = DbConfigCrypto(KEY).encrypt("secret")

        assertThatThrownBy { DbConfigCrypto(OTHER_KEY).decryptIfEncrypted(enc) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun 키는_Base64_32바이트여야_한다() {
        assertThatThrownBy { DbConfigCrypto("not base64!") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DbConfigCrypto(Base64.getEncoder().encodeToString(ByteArray(16))) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private companion object {
        private val KEY: String = Base64.getEncoder().encodeToString(ByteArray(32))
        private val OTHER_KEY: String = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".toByteArray())
    }
}
