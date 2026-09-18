package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 세션 빈이 싱글턴(TenantContext)에 주입되어도 실제로 해결되는지 확인한다.
 * @SessionScope 는 기본이 프록시 모드라 동작하지만, 그 가정을 코드로 고정한다.
 */
@SpringBootTest
@ActiveProfiles("local")
class SelectedTenantWiringTest {

    @Autowired SelectedTenant selectedTenant;

    @Test void 세션_빈이_싱글턴에_주입되어_해결된다() {
        assertThat(selectedTenant).isNotNull();
        assertThat(selectedTenant.companyIdx()).isEmpty();
    }
}
