package com.crosscert.fidoadmin.audit

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.argumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * `AuditLogger.log(AuditType, String)` 이 행위자 소속이 아니라
 * **대상 테넌트**를 COMPANY_IDX 에 남기는지 확인한다.
 */
class AuditLoggerTenantTest {

    private val writer: AuditLogWriter = mock(AuditLogWriter::class.java)
    private val selected = SelectedTenant()
    private val tenant = TenantContext(selected)
    private val companies: CompanyLookup = mock(CompanyLookup::class.java)
    private val logger = AuditLogger(writer, tenant, companies)
    private val captor: KArgumentCaptor<CcfaAuditLog> = argumentCaptor<CcfaAuditLog>()

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "user$companyIdx", null, "n", companyIdx, "c$companyIdx", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    private fun loginSuperSelecting(targetIdx: Long) {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼관리자", 0L, "SUPER", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(u, null, u.authorities)
        selected.select(targetIdx)
        whenever(companies.name(targetIdx)).thenReturn("고객사$targetIdx")
    }

    @Test fun SUPER_의_행위는_대상_테넌트로_기록된다() {
        loginSuperSelecting(9L)
        logger.log(AuditType.UPDATE, "CCFA_LICENSE UPDATE 1")
        verify(writer).write(captor.capture())
        assertThat(captor.lastValue.companyIdx).isEqualTo(9L)
        assertThat(captor.lastValue.userId).isEqualTo("superuser")
        // companies.name(...) 이 실제로 호출되어 대상 테넌트의 이름(행위자 소속 "SUPER" 가 아니라)이 남는지 확인한다.
        assertThat(captor.lastValue.companyName).isEqualTo("고객사9")
    }

    /**
     * COMPANY 도 hasTenant() 는 참(자기 소속이 곧 유효 테넌트)이므로 companies.name(...) 을
     * 통해 대상 테넌트 이름을 구한다 — CompanyLookup.name() 이 COMPANY 자신의 IDX 조회는
     * 허용하므로(자기 것이니까) 이 경로가 DB 상의 실제 이름을 돌려준다.
     */
    @Test fun COMPANY_의_행위는_자기_고객사로_기록된다() {
        login(5L)
        whenever(companies.name(5L)).thenReturn("c5")
        logger.log(AuditType.UPDATE, "CCFA_LICENSE UPDATE 1")
        verify(writer).write(captor.capture())
        assertThat(captor.lastValue.companyIdx).isEqualTo(5L)
        assertThat(captor.lastValue.companyName).isEqualTo("c5")
    }

    /** 시스템 영역 작업은 선택이 없다. 행위자 소속(0)으로 남긴다. */
    @Test fun 미선택_상태의_행위는_행위자_소속으로_기록된다() {
        login(0L)
        logger.log(AuditType.CREATE, "CCFA_COMPANY CREATE 3")
        verify(writer).write(captor.capture())
        assertThat(captor.lastValue.companyIdx).isEqualTo(0L)
        // 미선택 SUPER 는 hasTenant() 가 거짓이므로 행위자 소속(actor.getCompanyName())을 그대로 쓴다.
        assertThat(captor.lastValue.companyName).isEqualTo("c0")
        verify(companies, never()).name(anyOrNull()) // DB 조회가 필요 없는 경로다.
    }

    /** 해시는 저장되는 값으로 계산되므로 재계산이 성립해야 한다. */
    /**
     * 전역 테이블(고객사 구분 없음) 작업은 고객사를 선택한 채 해도 그 고객사로 남기지 않는다.
     * 그 고객사 운영자가 /logs/audit 에서 다른 고객사·슈퍼매니저의 변경 내용을 읽게 되기 때문이다 (진단 75).
     */
    @Test fun 전역_테이블_작업은_선택과_상관없이_행위자_소속으로_기록된다() {
        loginSuperSelecting(9L)
        logger.logGlobal(AuditType.UPDATE, "CCFA_COMPANY UPDATE 3")
        verify(writer).write(captor.capture())
        assertThat(captor.lastValue.companyIdx).isEqualTo(0L)
        assertThat(captor.lastValue.companyName).isEqualTo("SUPER")
        assertThat(captor.lastValue.userId).isEqualTo("superuser")
        assertThat(captor.lastValue.intergrityHash).isEqualTo(AuditLogger.integrityHash(captor.lastValue))
        verify(companies, never()).name(any())
    }

    @Test fun 전역_기록도_로그인_사용자가_없으면_남기지_않는다() {
        logger.logGlobal(AuditType.UPDATE, "CCFA_COMPANY UPDATE 3")
        verify(writer, never()).write(any())
    }

    @Test fun 무결성_해시가_저장값과_일치한다() {
        loginSuperSelecting(9L)
        logger.log(AuditType.UPDATE, "x")
        verify(writer).write(captor.capture())
        val row = captor.lastValue
        assertThat(row.intergrityHash).isEqualTo(AuditLogger.integrityHash(row))
    }

    /**
     * companies.name(...) 은 DB 를 조회한다. 이 조회가 실패해도
     * "실패해도 호출자 트랜잭션을 깨지 않는다"는 AuditLogger 의 불변식이 지켜져야 한다 —
     * 예외가 호출자에게 전파되지 않고, writer.write 도 호출되지 않은 채 조용히 삼켜져야 한다.
     */
    @Test fun 대상_테넌트_이름_조회_실패는_호출자에게_전파되지_않는다() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼관리자", 0L, "SUPER", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(u, null, u.authorities)
        selected.select(9L)
        whenever(companies.name(anyOrNull())).thenThrow(DataAccessResourceFailureException("DB down"))

        logger.log(AuditType.UPDATE, "CCFA_LICENSE UPDATE 1") // 예외가 전파되지 않아야 한다

        verify(writer, never()).write(any())
    }
}
