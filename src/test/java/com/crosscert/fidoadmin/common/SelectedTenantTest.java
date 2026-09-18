package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SelectedTenantTest {

    @Test void 처음에는_선택이_없다() {
        assertThat(new SelectedTenant().companyIdx()).isEmpty();
    }

    @Test void 선택하면_값이_남는다() {
        SelectedTenant t = new SelectedTenant();
        t.select(7L);
        assertThat(t.companyIdx()).contains(7L);
    }

    @Test void 해제하면_비워진다() {
        SelectedTenant t = new SelectedTenant();
        t.select(7L);
        t.clear();
        assertThat(t.companyIdx()).isEmpty();
    }

    /**
     * IDX 0 은 SUPER 를 뜻한다. 테넌트로 선택되면 유효 테넌트가 0 이 되어
     * COMPANY_IDX = 0 인 행(슈퍼관리자 계정 등)이 일반 테넌트 화면에 섞인다.
     */
    @Test void 전역_고객사는_선택할_수_없다() {
        assertThatThrownBy(() -> new SelectedTenant().select(0L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void null_은_선택할_수_없다() {
        assertThatThrownBy(() -> new SelectedTenant().select(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
