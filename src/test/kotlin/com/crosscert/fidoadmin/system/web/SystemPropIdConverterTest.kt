package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SystemPropIdConverterTest {

    val converter = SystemPropIdConverter()

    @Test fun convertsPathValue() {
        assertThat(converter.convert("SERVICE_NAME@1")).isEqualTo(CcfaSystemPropId("SERVICE_NAME", 1L))
    }

    @Test fun rejectsMalformed() {
        assertThatThrownBy { converter.convert("SERVICE_NAME") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
