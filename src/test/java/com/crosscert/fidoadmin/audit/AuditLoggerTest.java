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
        // 필드 경계를 <길이>:<값>| 로 명시해 "|" 가 값에 들어와도 모호해지지 않게 한다.
        String expected = Sha256PasswordEncoder.sha256Hex(
            f("1") + f("KB국민은행") + f("UPDATE") + f("kbadmin") + f("KB운영자")
                + f("APPID UPDATE 1") + f("10.0.0.5") + f("Mozilla/5.0")
                + f(row.getCreatedtime().toString()));
        assertThat(row.getIntergrityHash()).isEqualTo(expected);
        // 저장된 행에서 재계산해도 같은 값이어야 한다(무결성 검증 가능).
        assertThat(AuditLogger.integrityHash(row)).isEqualTo(expected);
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

    /**
     * CCFA_AUDIT_LOG 는 모든 VARCHAR2 가 BYTE 의미(NLS_LENGTH_SEMANTICS=BYTE)다.
     * 한글은 UTF-8 에서 3바이트이므로 문자 수로 자르면 ORA-12899 로 INSERT 가 실패하고,
     * AuditLogger 가 예외를 삼키는 탓에 감사 기록이 통째로 사라진다.
     */
    @Test
    void truncatesByUtf8BytesNotChars() {
        String longKorean = "가".repeat(200);   // 600 bytes
        ManagerUserDetails koreanActor =
            new ManagerUserDetails(1L, "kbadmin", null, longKorean, 1L, longKorean, true, true);

        logger.log(koreanActor, AuditType.UPDATE, longKorean, "10.0.0.5", longKorean);

        ArgumentCaptor<CcfaAuditLog> captor = ArgumentCaptor.forClass(CcfaAuditLog.class);
        verify(writer).write(captor.capture());
        CcfaAuditLog row = captor.getValue();
        assertThat(utf8Len(row.getUserName())).isLessThanOrEqualTo(32);
        assertThat(utf8Len(row.getCompanyName())).isLessThanOrEqualTo(512);
        assertThat(utf8Len(row.getMessage())).isLessThanOrEqualTo(4000);
        assertThat(utf8Len(row.getUa())).isLessThanOrEqualTo(2048);
        // 문자가 중간에서 깨지지 않아야 한다
        assertThat(row.getUserName()).doesNotContain("�").endsWith("가");
    }

    /** 구분자 "|" 가 값에 들어와도 필드 경계가 흐려지지 않아야 한다. */
    @Test
    void hashIsUnambiguousWhenValuesContainSeparator() {
        ManagerUserDetails a =
            new ManagerUserDetails(1L, "u", null, "alice|admin", 1L, "c", true, true);
        ManagerUserDetails b =
            new ManagerUserDetails(1L, "u", null, "alice", 1L, "c", true, true);

        logger.log(a, AuditType.UPDATE, "UPDATE 1", "1.1.1.1", "ua");
        logger.log(b, AuditType.UPDATE, "admin|UPDATE 1", "1.1.1.1", "ua");

        ArgumentCaptor<CcfaAuditLog> captor = ArgumentCaptor.forClass(CcfaAuditLog.class);
        verify(writer, org.mockito.Mockito.times(2)).write(captor.capture());
        var rows = captor.getAllValues();
        rows.get(1).setCreatedtime(rows.get(0).getCreatedtime()); // 시각 차이 배제
        assertThat(AuditLogger.integrityHash(rows.get(0)))
            .isNotEqualTo(AuditLogger.integrityHash(rows.get(1)));
    }

    /** 해시 인코딩 계약을 테스트 쪽에서 독립적으로 재현한다. */
    private static String f(String v) {
        return (v == null || v.isEmpty()) ? "-|" : v.length() + ":" + v + "|";
    }

    private static int utf8Len(String s) {
        return s == null ? 0 : s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
