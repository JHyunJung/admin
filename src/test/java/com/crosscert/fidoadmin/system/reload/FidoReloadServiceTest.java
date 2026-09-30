package com.crosscert.fidoadmin.system.reload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FidoReloadServiceTest {
    FidoReloadClient client = mock(FidoReloadClient.class);
    AuditLogger audit = mock(AuditLogger.class);
    FidoReloadService service = new FidoReloadService(client, audit);

    @Test void auditsSuccessAndFailureCounts() {
        when(client.reloadAll()).thenReturn(List.of(
            new ReloadResult("A", "http://a", true, "HTTP 200", 3),
            new ReloadResult("B", "http://b", false, "HTTP 500", 4)));
        assertThat(service.reloadNow()).hasSize(2);
        verify(audit).log(AuditType.UPDATE, "FIDO 서버 reload 수동 전송 | 성공 1 / 실패 1");
    }
}
