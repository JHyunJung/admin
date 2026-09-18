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

    @Test void defaultSortIsCreatetimeDescThenIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createtime", "idx"));
    }

    @Test void sortablePropertiesIncludeCreatetime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createtime");
    }

    @Test void idOfIsIdx() {
        Sign s = new Sign(); s.setIdx(3L);
        assertThat(service.idOf(s)).isEqualTo("3");
    }
}
