package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class DbConfigCryptoTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void 암호화한_값을_같은_키로_복호화한다() {
        DbConfigCrypto crypto = new DbConfigCrypto(KEY);
        String enc = crypto.encrypt("jdbc:oracle:thin:@db:1521/한글");

        assertThat(enc).startsWith("ENC(").endsWith(")");
        assertThat(crypto.decryptIfEncrypted(enc)).isEqualTo("jdbc:oracle:thin:@db:1521/한글");
    }

    @Test
    void 평문과_plain_접두어는_키_없이_그대로_돌려준다() {
        DbConfigCrypto crypto = new DbConfigCrypto(null);

        assertThat(crypto.decryptIfEncrypted("kbfido")).isEqualTo("kbfido");
        assertThat(crypto.decryptIfEncrypted("[plain]ENC(raw)")).isEqualTo("ENC(raw)");
        assertThat(crypto.decryptIfEncrypted(null)).isNull();
    }

    @Test
    void 암호문인데_키가_없으면_실패한다() {
        String enc = new DbConfigCrypto(KEY).encrypt("secret");

        assertThatThrownBy(() -> new DbConfigCrypto("").decryptIfEncrypted(enc))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DB_CONFIG_KEY");
    }

    @Test
    void 다른_키로는_복호화하지_못한다() {
        String enc = new DbConfigCrypto(KEY).encrypt("secret");

        assertThatThrownBy(() -> new DbConfigCrypto(OTHER_KEY).decryptIfEncrypted(enc))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키는_Base64_32바이트여야_한다() {
        assertThatThrownBy(() -> new DbConfigCrypto("not base64!"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DbConfigCrypto(Base64.getEncoder().encodeToString(new byte[16])))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
