package com.crosscert.fidoadmin.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 로그에 넣는 외부 값은 한 줄이어야 한다. 개행이 남으면 가짜 로그 줄을 만들 수 있다. */
class LogTextTest {

    @Test fun nullStaysNull() {
        assertThat(LogText.oneLine(null)).isNull()
    }

    @Test fun plainValueIsUnchanged() {
        assertThat(LogText.oneLine("server-01 https://10.0.0.1:8443/fido 한글")).isEqualTo("server-01 https://10.0.0.1:8443/fido 한글")
    }

    @Test fun replacesEachLineBreakAndControlCharacter() {
        assertThat(LogText.oneLine("a\r\nb\nc\td\u0000e\u0085f g h"))
            .isEqualTo("a__b_c_d_e_f_g_h")
    }

    @Test fun forgedLogLineCollapsesIntoOneLine() {
        val forged = "x\n2026-11-12 10:00:00 INFO [ClientAccess] Registered : evil"
        assertThat(LogText.oneLine(forged)).doesNotContain("\n").startsWith("x_2026")
    }
}
