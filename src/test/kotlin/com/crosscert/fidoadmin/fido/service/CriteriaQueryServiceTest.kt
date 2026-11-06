package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Criteria
import com.crosscert.fidoadmin.fido.repository.CriteriaRepository
import com.crosscert.fidoadmin.fido.web.CriteriaSearchForm
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException

class CriteriaQueryServiceTest {

    val repo: CriteriaRepository = mock()
    val audit: AuditLogger = mock()
    val jdbc: NamedParameterJdbcTemplate = mock()
    val events: ApplicationEventPublisher = mock()
    val parser = CriteriaMetadataParser()
    val service = CriteriaQueryService(repo, audit, TenantContext(SelectedTenant()), jdbc, events, parser)

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    private fun criteria(idx: Long, aaid: String): Criteria {
        val c = Criteria()
        c.idx = idx
        c.aaid = aaid
        return c
    }

    /** Java 의 `Long.class` (java.lang.Long). Kotlin `Long::class.java` 는 원시형 long 이라 다르다. */
    private val boxedLong: Class<Long> = Long::class.javaObjectType

    /**
     * CRITERIA 는 COMPANY_IDX 가 없는 전역 테이블이지만 이 화면은 SUPER 전용이 아니다.
     * 화면의 용도가 고객사별 토글(CCFA_COMPANY_AAID)이라 COMPANY 운영자가 쓴다.
     */
    @Test fun companyRoleCanSearchAndGet() {
        login(1L)
        whenever(repo.findAllExcluding(any(), any<Specification<Criteria>>(), any<PageRequest>(), any<Set<String>>()))
            .thenReturn(PageImpl(listOf()))
        whenever(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")))

        service.search(CriteriaSearchForm(), PageRequest.of(0, 20, service.defaultSort()))

        assertThat(service.get(1L).aaid).isEqualTo("0012#0001")
        verify(repo).findAllExcluding(any(), any<Specification<Criteria>>(), any<PageRequest>(), any<Set<String>>())
    }

    @Test fun superRoleCanSearchAndGet() {
        login(0L)
        whenever(repo.findAllExcluding(any(), any<Specification<Criteria>>(), any<PageRequest>(), any<Set<String>>()))
            .thenReturn(PageImpl(listOf()))
        whenever(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")))
        val f = CriteriaSearchForm()
        f.aaid = "0012"

        service.search(f, PageRequest.of(0, 20, service.defaultSort()))

        assertThat(service.get(1L).aaid).isEqualTo("0012#0001")
        verify(repo).findAllExcluding(any(), any<Specification<Criteria>>(), any<PageRequest>(), any<Set<String>>())
    }

    /** 로그인하지 않았으면 열리지 않는다. 전역 차단을 푼 뒤에도 이것은 남는다. */
    @Test fun anonymousIsRejected() {
        assertThatThrownBy { service.get(1L) }.isInstanceOf(IllegalStateException::class.java)
        verify(repo, never()).findById(anyOrNull())
    }

    /** 꺼 둔 AAID 조회는 유효 테넌트로 필터한다 — 남의 고객사 설정이 섞이면 안 된다. */
    @Test fun disabledAaidsFiltersByEffectiveTenant() {
        login(7L)
        whenever(jdbc.queryForList(anyString(), any<MapSqlParameterSource>(), eq(String::class.java)))
            .thenReturn(listOf("0012#0001"))

        assertThat(service.disabledAaids()).containsExactly("0012#0001")

        val params = ArgumentCaptor.forClass(MapSqlParameterSource::class.java)
        verify(jdbc).queryForList(anyString(), params.capture(), eq(String::class.java))
        assertThat(params.value.getValue("companyIdx")).isEqualTo(7L)
    }

    /** 비활성화는 차단 목록에 행을 넣는다. */
    @Test fun disablingInsertsBlockRowAndLogs() {
        login(7L)
        whenever(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")))
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(boxedLong))).thenReturn(7L)
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(1)

        assertThat(service.changeStatus(1L, false)).isTrue()

        val sql = ArgumentCaptor.forClass(String::class.java)
        verify(jdbc).update(sql.capture(), any<MapSqlParameterSource>())
        assertThat(sql.value).contains("INSERT INTO CCFA_COMPANY_AAID")
        verify(audit).log(eq(AuditType.STATUS), anyString())
    }

    /** 활성화는 차단 목록에서 행을 지운다. */
    @Test fun enablingDeletesBlockRow() {
        login(7L)
        whenever(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")))
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(boxedLong))).thenReturn(7L)
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(1)

        assertThat(service.changeStatus(1L, true)).isTrue()

        val sql = ArgumentCaptor.forClass(String::class.java)
        verify(jdbc).update(sql.capture(), any<MapSqlParameterSource>())
        assertThat(sql.value).contains("DELETE FROM CCFA_COMPANY_AAID")
    }

    /** 이미 그 상태면 false 를 주고 감사 로그를 남기지 않는다 — 바뀐 것이 없다. */
    @Test fun noChangeReportsFalseAndSkipsAudit() {
        login(7L)
        whenever(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")))
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(boxedLong))).thenReturn(7L)
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(0)

        assertThat(service.changeStatus(1L, true)).isFalse()

        verify(audit, never()).log(any<AuditType>(), anyString())
    }

    /** AAID 가 없는 행은 켜고 끌 대상이 없다. 차단 목록에 빈 값을 넣지 않는다. */
    @Test fun blankAaidIsRejected() {
        login(7L)
        whenever(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "  ")))

        assertThatThrownBy { service.changeStatus(1L, false) }
            .isInstanceOf(ResponseStatusException::class.java)

        verify(jdbc, never()).update(anyString(), any<MapSqlParameterSource>())
    }

    @Test fun defaultSortIsIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"))
    }

    @Test fun sortablePropertiesIncludeAaidAndUpdatedtime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "aaid", "updatedtime")
    }

    /** 전체 비활성은 아직 없는 AAID 만 넣는다(중복 행이 쌓이지 않는다). */
    @Test fun disableAllInsertsOnlyMissingRowsAndLogs() {
        login(7L)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(boxedLong))).thenReturn(7L)
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(5)

        assertThat(service.changeStatusAll(false)).isEqualTo(5)

        val sql = ArgumentCaptor.forClass(String::class.java)
        verify(jdbc).update(sql.capture(), any<MapSqlParameterSource>())
        assertThat(sql.value).contains("INSERT INTO CCFA_COMPANY_AAID").contains("NOT EXISTS").contains("FROM CRITERIA")
        verify(audit).log(eq(AuditType.STATUS), anyString())
    }

    @Test fun enableAllDeletesEveryRowOfTheTenant() {
        login(7L)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(boxedLong))).thenReturn(7L)
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(3)

        assertThat(service.changeStatusAll(true)).isEqualTo(3)

        val sql = ArgumentCaptor.forClass(String::class.java)
        val params = ArgumentCaptor.forClass(MapSqlParameterSource::class.java)
        verify(jdbc).update(sql.capture(), params.capture())
        assertThat(sql.value).contains("DELETE FROM CCFA_COMPANY_AAID").doesNotContain("AAID = :aaid")
        assertThat(params.value.getValue("companyIdx")).isEqualTo(7L)
    }

    @Test fun bulkNoChangeSkipsAudit() {
        login(7L)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(boxedLong))).thenReturn(7L)
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(0)

        assertThat(service.changeStatusAll(true)).isZero()

        verify(audit, never()).log(any<AuditType>(), anyString())
    }

    @Test fun toggleThatChangesPublishesReload() {
        login(7L)
        whenever(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")))
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(boxedLong))).thenReturn(7L)
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(1)
        assertThat(service.changeStatus(1L, false)).isTrue()
        verify(events).publishEvent(FidoConfigChanged("AAID 상태 변경 0012#0001"))
    }

    // never().publishEvent(any()) 는 Java 원본이 컴파일한 대로 publishEvent(ApplicationEvent) 오버로드를 본다.
    @Test fun toggleWithNoChangeDoesNotPublish() {
        login(7L)
        whenever(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")))
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(boxedLong))).thenReturn(7L)
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(0)
        assertThat(service.changeStatus(1L, false)).isFalse()
        verify(events, never()).publishEvent(anyOrNull<ApplicationEvent>())
    }

    @Test fun bulkToggleWithChangesPublishes() {
        login(7L)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(boxedLong))).thenReturn(7L)
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(4)
        assertThat(service.changeStatusAll(false)).isEqualTo(4)
        verify(events).publishEvent(FidoConfigChanged("AAID 전체 비활성 4건"))
    }

    @Test fun bulkToggleWithoutChangesDoesNotPublish() {
        login(7L)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(boxedLong))).thenReturn(7L)
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(0)
        assertThat(service.changeStatusAll(true)).isZero()
        verify(events, never()).publishEvent(anyOrNull<ApplicationEvent>())
    }

    @Test fun disableAllForInsertsForGivenCompanyWithoutAuditOrEvent() {
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(4)
        assertThat(service.disableAllFor(9L)).isEqualTo(4)
        val sql = ArgumentCaptor.forClass(String::class.java)
        val params = ArgumentCaptor.forClass(MapSqlParameterSource::class.java)
        verify(jdbc).update(sql.capture(), params.capture())
        assertThat(sql.value).contains("INSERT INTO CCFA_COMPANY_AAID").contains("SELECT DISTINCT").contains("NOT EXISTS")
        assertThat(params.value.getValue("companyIdx")).isEqualTo(9L)
        verify(audit, never()).log(anyOrNull(), anyString())
        verify(events, never()).publishEvent(anyOrNull<ApplicationEvent>())
    }

    @Test fun deleteAllForDeletesGivenCompanyRows() {
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(2)
        assertThat(service.deleteAllFor(9L)).isEqualTo(2)
        val sql = ArgumentCaptor.forClass(String::class.java)
        verify(jdbc).update(sql.capture(), any<MapSqlParameterSource>())
        assertThat(sql.value).contains("DELETE FROM CCFA_COMPANY_AAID")
    }

    @Test fun superCreatesCriteriaBlocksEveryCompanyAndPublishes() {
        login(0L)
        whenever(repo.existsByAaid("0012#0009")).thenReturn(false)
        whenever(repo.save(any<Criteria>())).thenAnswer { inv -> val c = inv.getArgument<Criteria>(0); c.idx = 30L; c }
        whenever(jdbc.update(anyString(), any<MapSqlParameterSource>())).thenReturn(4)

        val saved = service.create("{\"aaid\":\"0012#0009\",\"keyProtection\":2}")

        assertThat(saved.idx).isEqualTo(30L)
        assertThat(saved.keyprotection).isEqualTo(2L)
        assertThat(saved.createtime).isNotNull()
        assertThat(saved.updatedtime).isEqualTo(saved.createtime)
        val sql = ArgumentCaptor.forClass(String::class.java)
        val params = ArgumentCaptor.forClass(MapSqlParameterSource::class.java)
        verify(jdbc).update(sql.capture(), params.capture())
        assertThat(sql.value).contains("INSERT INTO CCFA_COMPANY_AAID").contains("FROM CCFA_COMPANY").contains("NOT EXISTS")
        assertThat(params.value.getValue("aaid")).isEqualTo("0012#0009")
        verify(audit).log(AuditType.CREATE, "AAID(정책) 등록 | AAID: 0012#0009 | 차단 고객사 4곳")
        verify(events).publishEvent(FidoConfigChanged("AAID 등록 0012#0009"))
    }

    /** 중복 검사는 trim 된 AAID 로 한다. 앞뒤 공백으로 같은 AAID 가 두 번 들어가면 안 된다. */
    @Test fun duplicateAaidIsRejected() {
        login(0L)
        whenever(repo.existsByAaid("0012#0001")).thenReturn(true)

        assertThatThrownBy { service.create("{\"aaid\":\" 0012#0001 \"}") }
            .isInstanceOf(CriteriaMetadataException::class.java)
            .hasMessage("이미 등록된 AAID 입니다.")

        verify(repo, never()).save(anyOrNull<Criteria>())
        verify(jdbc, never()).update(anyString(), any<MapSqlParameterSource>())
    }

    @Test fun companyRoleCannotCreate() {
        login(7L)
        assertThatThrownBy { service.create("{\"aaid\":\"0012#0009\"}") }
            .isInstanceOf(AccessDeniedException::class.java)
        verify(repo, never()).save(anyOrNull<Criteria>())
    }

    @Test fun invalidMetadataSavesNothing() {
        login(0L)
        assertThatThrownBy { service.create("not json") }
            .isInstanceOf(CriteriaMetadataException::class.java)
            .hasMessage(CriteriaMetadataParser.INVALID)
        verify(repo, never()).save(anyOrNull<Criteria>())
        verify(events, never()).publishEvent(anyOrNull<ApplicationEvent>())
    }
}
