package com.crosscert.fidoadmin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditLoggerTest {

    private final AuditLogWriter writer = mock(AuditLogWriter.class);
    private final AuditLogger logger = new AuditLogger(writer);
    private final ManagerUserDetails actor =
        new ManagerUserDetails(1L, "kbadmin", null, "KB운영자", 1L, "KB국민은행", true, true);

    @Test
    void writesAllColumnsAndIntegrityHash() {
        logger.log(actor, AuditType.UPDATE, "APPID UPDATE 1", "10.0.0.5", "Mozilla/5.0");

        ArgumentCaptor<CcfaAuditLog> captor = ArgumentCaptor.forClass(CcfaAuditLog.class);
        verify(writer).write(captor.capture());
        CcfaAuditLog row = captor.getValue();
        assertThat(row.getCompanyIdx()).isEqualTo(1L);
        assertThat(row.getCompanyName()).isEqualTo("KB국민은행");
        assertThat(row.getType()).isEqualTo("UPDATE");
        assertThat(row.getUserId()).isEqualTo("kbadmin");
        assertThat(row.getUserName()).isEqualTo("KB운영자");
        assertThat(row.getMessage()).isEqualTo("APPID UPDATE 1");
        assertThat(row.getIp()).isEqualTo("10.0.0.5");
        assertThat(row.getUa()).isEqualTo("Mozilla/5.0");
        assertThat(row.getCreatedtime()).isNotNull();
        String expected = Sha256PasswordEncoder.sha256Hex(
            "1|KB국민은행|UPDATE|kbadmin|KB운영자|APPID UPDATE 1|10.0.0.5|Mozilla/5.0|" + row.getCreatedtime());
        assertThat(row.getIntergrityHash()).isEqualTo(expected);
    }

    @Test
    void truncatesIpToFifteenCharsAndLongFields() {
        logger.log(actor, AuditType.LOGIN, "x".repeat(5000), "2001:0db8:85a3:0000:0000:8a2e:0370:7334", "u".repeat(3000));

        ArgumentCaptor<CcfaAuditLog> captor = ArgumentCaptor.forClass(CcfaAuditLog.class);
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().getIp()).hasSize(15);
        assertThat(captor.getValue().getMessage()).hasSize(4000);
        assertThat(captor.getValue().getUa()).hasSize(2048);
    }

    @Test
    void swallowsWriterFailure() {
        doThrow(new RuntimeException("db down")).when(writer).write(any());
        logger.log(actor, AuditType.DELETE, "m", "1.1.1.1", "ua"); // 예외가 전파되지 않아야 한다
    }
}
