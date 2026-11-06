package com.crosscert.fidoadmin.common

import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * DB 의 VARCHAR2 는 BYTE 의미다. 저장 전에 자를 때는 바이트로 세되, 멀티바이트 문자가
 * 경계에서 쪼개져 깨진 글자가 저장되지 않도록 문자 경계에서 끊는다.
 * AuditLogger 와 SignupService 가 각자 들고 있던 같은 로직을 여기 한 곳으로 모은다.
 */
class Utf8Test {

    @Test fun nullStaysNull() {
        assertThat(Utf8.truncateToBytes(null, 10)).isNull()
    }

    @Test fun shortValueIsReturnedAsIs() {
        assertThat(Utf8.truncateToBytes("abc", 10)).isEqualTo("abc")
        assertThat(Utf8.truncateToBytes("가나다", 9)).isEqualTo("가나다") // 정확히 9바이트
    }

    @Test fun cutsAsciiAtByteLimit() {
        assertThat(Utf8.truncateToBytes("abcdef", 4)).isEqualTo("abcd")
    }

    /** 한글 1자 = 3바이트. 10바이트 한도에 "가나다" (9) 까지만 남고, 넷째 글자의 첫 바이트는 버린다. */
    @Test fun cutsKoreanAtCharacterBoundary() {
        val cut = Utf8.truncateToBytes("가나다라", 10)
        assertThat(cut).isEqualTo("가나다")
        assertThat(cut!!.toByteArray(StandardCharsets.UTF_8).size).isLessThanOrEqualTo(10)
    }

    @Test fun neverExceedsLimitForMixedText() {
        val s = "a가b나c다d라e마"
        for (max in 0..s.toByteArray(StandardCharsets.UTF_8).size) {
            val cut = Utf8.truncateToBytes(s, max)
            assertThat(cut!!.toByteArray(StandardCharsets.UTF_8).size).isLessThanOrEqualTo(max)
            assertThat(s).startsWith(cut)
        }
    }
}
