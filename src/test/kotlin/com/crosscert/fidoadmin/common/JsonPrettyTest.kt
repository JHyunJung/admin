package com.crosscert.fidoadmin.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JsonPrettyTest {

    @Test fun prettyPrintsObject() {
        val out = JsonPretty.pretty("{\"op\":\"Auth\",\"result\":\"1200\"}")
        assertThat(out).isEqualTo("{\n  \"op\" : \"Auth\",\n  \"result\" : \"1200\"\n}")
    }

    @Test fun returnsRawWhenNotJson() {
        assertThat(JsonPretty.pretty("not json {")).isEqualTo("not json {")
    }

    @Test fun passesThroughNullAndBlank() {
        assertThat(JsonPretty.pretty(null)).isNull()
        assertThat(JsonPretty.pretty("  ")).isEqualTo("  ")
    }

    @Test fun isValidJsonDetectsStructure() {
        assertThat(JsonPretty.isValidJson("{\"a\":1}")).isTrue()
        assertThat(JsonPretty.isValidJson("[1,2]")).isTrue()
        assertThat(JsonPretty.isValidJson("{a:1}")).isFalse()
        assertThat(JsonPretty.isValidJson("")).isFalse()
    }

    /** Jackson readTree() 는 기본적으로 첫 JSON 값만 읽고 뒤를 무시한다. 후행 토큰이 있으면 유효하지 않은 입력으로 취급해야 한다. */
    @Test fun prettyRejectsTrailingTokensAndReturnsRaw() {
        assertThat(JsonPretty.pretty("{} garbage")).isEqualTo("{} garbage")
        assertThat(JsonPretty.pretty("{} []")).isEqualTo("{} []")
    }

    @Test fun isValidJsonRejectsTrailingTokens() {
        assertThat(JsonPretty.isValidJson("{} garbage")).isFalse()
        assertThat(JsonPretty.isValidJson("{} []")).isFalse()
    }
}
