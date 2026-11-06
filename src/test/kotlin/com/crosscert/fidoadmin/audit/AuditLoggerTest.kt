package com.crosscert.fidoadmin.audit

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.argumentCaptor
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any

class AuditLoggerTest {

    private val writer: AuditLogWriter = mock(AuditLogWriter::class.java)
    private val logger =
        AuditLogger(writer, TenantContext(SelectedTenant()), mock(CompanyLookup::class.java))
    private val actor =
        ManagerUserDetails(1L, "kbadmin", null, "KB운영자", 1L, "KB국민은행", true, true)

    @Test
    fun writesAllColumnsAndIntegrityHash() {
        logger.log(actor, AuditType.UPDATE, "APPID UPDATE 1", "10.0.0.5", "Mozilla/5.0")

        val captor = argumentCaptor<CcfaAuditLog>()
        verify(writer).write(captor.capture())
        val row = captor.lastValue
        assertThat(row.companyIdx).isEqualTo(1L)
        assertThat(row.companyName).isEqualTo("KB국민은행")
        assertThat(row.type).isEqualTo("UPDATE")
        assertThat(row.userId).isEqualTo("kbadmin")
        assertThat(row.userName).isEqualTo("KB운영자")
        assertThat(row.message).isEqualTo("APPID UPDATE 1")
        assertThat(row.ip).isEqualTo("10.0.0.5")
        assertThat(row.ua).isEqualTo("Mozilla/5.0")
        assertThat(row.createdtime).isNotNull()
        // 필드 경계를 <길이>:<값>| 로 명시해 "|" 가 값에 들어와도 모호해지지 않게 한다.
        val expected = Sha256PasswordEncoder.sha256Hex(
            f("1") + f("KB국민은행") + f("UPDATE") + f("kbadmin") + f("KB운영자") +
                f("APPID UPDATE 1") + f("10.0.0.5") + f("Mozilla/5.0") +
                f(row.createdtime.toString()))
        assertThat(row.intergrityHash).isEqualTo(expected)
        // 저장된 행에서 재계산해도 같은 값이어야 한다(무결성 검증 가능).
        assertThat(AuditLogger.integrityHash(row)).isEqualTo(expected)
    }

    @Test
    fun truncatesIpToFifteenCharsAndLongFields() {
        logger.log(actor, AuditType.LOGIN, "x".repeat(5000), "2001:0db8:85a3:0000:0000:8a2e:0370:7334", "u".repeat(3000))

        val captor = argumentCaptor<CcfaAuditLog>()
        verify(writer).write(captor.capture())
        assertThat(captor.lastValue.ip).hasSize(15)
        assertThat(captor.lastValue.message).hasSize(4000)
        assertThat(captor.lastValue.ua).hasSize(2048)
    }

    @Test
    fun swallowsWriterFailure() {
        doThrow(RuntimeException("db down")).`when`(writer).write(any())
        logger.log(actor, AuditType.DELETE, "m", "1.1.1.1", "ua") // 예외가 전파되지 않아야 한다
    }

    /**
     * CCFA_AUDIT_LOG 는 모든 VARCHAR2 가 BYTE 의미(NLS_LENGTH_SEMANTICS=BYTE)다.
     * 한글은 UTF-8 에서 3바이트이므로 문자 수로 자르면 ORA-12899 로 INSERT 가 실패하고,
     * AuditLogger 가 예외를 삼키는 탓에 감사 기록이 통째로 사라진다.
     */
    @Test
    fun truncatesByUtf8BytesNotChars() {
        val longKorean = "가".repeat(200)   // 600 bytes
        val koreanActor =
            ManagerUserDetails(1L, "kbadmin", null, longKorean, 1L, longKorean, true, true)

        logger.log(koreanActor, AuditType.UPDATE, longKorean, "10.0.0.5", longKorean)

        val captor = argumentCaptor<CcfaAuditLog>()
        verify(writer).write(captor.capture())
        val row = captor.lastValue
        assertThat(utf8Len(row.userName)).isLessThanOrEqualTo(32)
        assertThat(utf8Len(row.companyName)).isLessThanOrEqualTo(512)
        assertThat(utf8Len(row.message)).isLessThanOrEqualTo(4000)
        assertThat(utf8Len(row.ua)).isLessThanOrEqualTo(2048)
        // 문자가 중간에서 깨지지 않아야 한다
        assertThat(row.userName).doesNotContain("�").endsWith("가")
    }

    /** 구분자 "|" 가 값에 들어와도 필드 경계가 흐려지지 않아야 한다. */
    @Test
    fun hashIsUnambiguousWhenValuesContainSeparator() {
        val a = ManagerUserDetails(1L, "u", null, "alice|admin", 1L, "c", true, true)
        val b = ManagerUserDetails(1L, "u", null, "alice", 1L, "c", true, true)

        logger.log(a, AuditType.UPDATE, "UPDATE 1", "1.1.1.1", "ua")
        logger.log(b, AuditType.UPDATE, "admin|UPDATE 1", "1.1.1.1", "ua")

        val captor = argumentCaptor<CcfaAuditLog>()
        verify(writer, times(2)).write(captor.capture())
        val rows = captor.allValues
        rows[1].createdtime = rows[0].createdtime // 시각 차이 배제
        assertThat(AuditLogger.integrityHash(rows[0]))
            .isNotEqualTo(AuditLogger.integrityHash(rows[1]))
    }

    private companion object {
        /** 해시 인코딩 계약을 테스트 쪽에서 독립적으로 재현한다. */
        fun f(v: String?): String = if (v == null || v.isEmpty()) "-|" else v.length.toString() + ":" + v + "|"

        fun utf8Len(s: String?): Int = s?.toByteArray(StandardCharsets.UTF_8)?.size ?: 0
    }
}
