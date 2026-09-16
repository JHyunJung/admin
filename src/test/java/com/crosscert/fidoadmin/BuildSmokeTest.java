package com.crosscert.fidoadmin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuildSmokeTest {
    @Test
    void mainClassExists() {
        assertThat(FidoAdminApplication.class.getSimpleName()).isEqualTo("FidoAdminApplication");
    }
}
