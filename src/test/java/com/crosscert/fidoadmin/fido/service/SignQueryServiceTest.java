package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.fido.entity.Sign;
import com.crosscert.fidoadmin.fido.repository.SignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class SignQueryServiceTest {

    SignQueryService service = new SignQueryService(mock(SignRepository.class), mock(AuditLogger.class), new TenantContext(new SelectedTenant()));

    /** 시퀀스 PK 역순. PK 인덱스를 거꾸로 읽어 첫 페이지만 가져온다(생성 시각 정렬은 전체 정렬이 필요하다). */
    @Test void defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }

    @Test void sortablePropertiesIncludeCreatetime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createtime");
    }

    @Test void idOfIsIdx() {
        Sign s = new Sign(); s.setIdx(3L);
        assertThat(service.idOf(s)).isEqualTo("3");
    }
}
