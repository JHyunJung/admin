package com.crosscert.fidoadmin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BuildSmokeTest {
    @Test
    fun mainClassExists() {
        assertThat(FidoAdminApplication::class.java.simpleName).isEqualTo("FidoAdminApplication")
    }
}
