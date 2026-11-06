package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Sign
import com.crosscert.fidoadmin.fido.repository.SignRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.data.domain.Sort

class SignQueryServiceTest {

    val service = SignQueryService(mock<SignRepository>(), mock<AuditLogger>(), TenantContext(SelectedTenant()))

    /** 시퀀스 PK 역순. PK 인덱스를 거꾸로 읽어 첫 페이지만 가져온다(생성 시각 정렬은 전체 정렬이 필요하다). */
    @Test fun defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
    }

    @Test fun sortablePropertiesIncludeCreatetime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createtime")
    }

    @Test fun idOfIsIdx() {
        val s = Sign(); s.idx = 3L
        assertThat(service.idOf(s)).isEqualTo("3")
    }
}
