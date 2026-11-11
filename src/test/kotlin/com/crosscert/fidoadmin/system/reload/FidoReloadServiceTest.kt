package com.crosscert.fidoadmin.system.reload

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FidoReloadServiceTest {
    val client: FidoReloadClient = mock()
    val audit: AuditLogger = mock()
    val service = FidoReloadService(client, audit)

    @Test fun auditsSuccessAndFailureCounts() {
        whenever(client.reloadAll()).thenReturn(listOf(
            ReloadResult("A", "http://a", true, "HTTP 200", 3),
            ReloadResult("B", "http://b", false, "HTTP 500", 4)))
        assertThat(service.reloadNow()).hasSize(2)
        verify(audit).logGlobal(AuditType.UPDATE, "FIDO 서버 reload 수동 전송 | 성공 1 / 실패 1")
    }
}
