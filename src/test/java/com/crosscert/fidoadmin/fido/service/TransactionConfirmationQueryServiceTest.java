package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;
import com.crosscert.fidoadmin.fido.repository.TransactionConfirmationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class TransactionConfirmationQueryServiceTest {

    TransactionConfirmationQueryService service =
        new TransactionConfirmationQueryService(mock(TransactionConfirmationRepository.class), mock(AuditLogger.class));

    /** 이 테이블의 시각 컬럼은 CREATEDTIME(다른 FIDO 테이블은 CREATETIME). 이름을 틀리면 조회 시 500. */
    @Test void defaultSortIsCreatedtimeDescThenIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdtime", "idx"));
    }

    @Test void sortablePropertiesIncludeCreatedtime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createdtime");
    }

    @Test void idOfIsIdx() {
        TransactionConfirmation t = new TransactionConfirmation(); t.setIdx(8L);
        assertThat(service.idOf(t)).isEqualTo("8");
    }
}
