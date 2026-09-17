package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.fido.entity.Transactionhash;
import com.crosscert.fidoadmin.fido.repository.TransactionhashRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class TransactionhashQueryServiceTest {

    TransactionhashQueryService service =
        new TransactionhashQueryService(mock(TransactionhashRepository.class), mock(AuditLogger.class));

    @Test void defaultSortIsCreatetimeDescThenIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createtime", "idx"));
    }

    @Test void sortablePropertiesIncludeCreatetime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createtime");
    }

    @Test void idOfIsIdx() {
        Transactionhash t = new Transactionhash(); t.setIdx(11L);
        assertThat(service.idOf(t)).isEqualTo("11");
    }
}
