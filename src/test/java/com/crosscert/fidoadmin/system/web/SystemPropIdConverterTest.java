package com.crosscert.fidoadmin.system.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import org.junit.jupiter.api.Test;

class SystemPropIdConverterTest {

    SystemPropIdConverter converter = new SystemPropIdConverter();

    @Test void convertsPathValue() {
        assertThat(converter.convert("SERVICE_NAME@1")).isEqualTo(new CcfaSystemPropId("SERVICE_NAME", 1L));
    }

    @Test void rejectsMalformed() {
        assertThatThrownBy(() -> converter.convert("SERVICE_NAME")).isInstanceOf(IllegalArgumentException.class);
    }
}
