package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Transactionhash
import com.crosscert.fidoadmin.fido.repository.TransactionhashRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.data.domain.Sort

class TransactionhashQueryServiceTest {

    val service =
        TransactionhashQueryService(mock<TransactionhashRepository>(), mock<AuditLogger>(), TenantContext(SelectedTenant()))

    /** 시퀀스 PK 역순. PK 인덱스를 거꾸로 읽어 첫 페이지만 가져온다(생성 시각 정렬은 전체 정렬이 필요하다). */
    @Test fun defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
    }

    @Test fun sortablePropertiesIncludeCreatetime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createtime")
    }

    @Test fun idOfIsIdx() {
        val t = Transactionhash(); t.idx = 11L
        assertThat(service.idOf(t)).isEqualTo("11")
    }
}
