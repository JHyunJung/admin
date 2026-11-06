package com.crosscert.fidoadmin.log.service

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.log.web.FidoLogRow
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.function.Function
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException

/**
 * FIDO 로그는 날짜별로 테이블이 갈린다. 테이블 이름이 SQL 에 문자열로 들어가는 유일한
 * 자리이므로, 그 이름이 어떻게 만들어지는지와 테넌트 조건이 빠지지 않는지를 확인한다.
 */
class FidoLogQueryServiceTest {

    val jdbc: NamedParameterJdbcTemplate = mock()
    val service = FidoLogQueryService(jdbc, TenantContext(SelectedTenant()), FidoLogTable(jdbc))

    @BeforeEach fun loginCompany() {
        val u = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    /** 테이블이 있다고 답하게 한다. */
    private fun tableExists(exists: Boolean) {
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Int::class.javaObjectType)))
            .thenReturn(if (exists) 1 else 0)
    }

    private fun form(date: LocalDate): FidoLogSearchForm {
        val f = FidoLogSearchForm()
        f.logDate = date
        return f
    }

    /** jdbc.query(String, SqlParameterSource, RowMapper) 를 고르는 매처. */
    private fun anyMapper(): RowMapper<Any> = any()

    private fun idxOf(): Function<FidoLogRow, Long?> = Function { it.idx }

    /** 그날 로그가 없으면 테이블 자체가 없다. 오류가 아니라 빈 목록이다. */
    @Test fun missingTableYieldsEmptyPageInsteadOfError() {
        tableExists(false)

        val page = service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20)).page

        assertThat(page.totalElements).isZero()
        assertThat(page.content).isEmpty()
        // 존재 확인 외에 목록 조회로 나가지 않는다.
        verify(jdbc, never()).query(anyString(), any<MapSqlParameterSource>(), anyMapper())
    }

    /** 목록은 그 날짜의 테이블을 읽고, 유효 테넌트 조건을 반드시 건다. */
    @Test fun searchTargetsDateTableAndFiltersByTenant() {
        tableExists(true)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))).thenReturn(3L)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper()))
            .thenReturn(java.util.List.of())

        service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20))

        val sql = argumentCaptor<String>()
        val params = argumentCaptor<MapSqlParameterSource>()
        verify(jdbc).query(sql.capture(), params.capture(), anyMapper())

        assertThat(sql.firstValue).contains("FIDO_LOGS_20260926")
        assertThat(sql.firstValue).contains("COMPANY_IDX = :companyIdx")
        assertThat(params.firstValue.getValue("companyIdx")).isEqualTo(1L)
    }

    /** 총 건수가 0이면 목록 조회로 나가지 않는다. */
    @Test fun zeroCountSkipsRowQuery() {
        tableExists(true)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))).thenReturn(0L)

        val page = service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20)).page

        assertThat(page.totalElements).isZero()
        verify(jdbc, never()).query(anyString(), any<MapSqlParameterSource>(), anyMapper())
    }

    /** 상세도 유효 테넌트를 건다. 다른 고객사의 행은 조회되지 않아 404 가 된다. */
    @Test fun detailFiltersByTenantAndIsNotFoundWhenAbsent() {
        tableExists(true)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper()))
            .thenReturn(java.util.List.of())

        assertThatThrownBy { service.get(LocalDate.of(2026, 9, 26), 5L) }
            .isInstanceOf(ResponseStatusException::class.java)

        val params = argumentCaptor<MapSqlParameterSource>()
        verify(jdbc).query(anyString(), params.capture(), anyMapper())
        assertThat(params.firstValue.getValue("companyIdx")).isEqualTo(1L)
    }

    /** 없는 날짜의 상세는 404 다(500 이 아니다). */
    @Test fun detailOnMissingTableIsNotFound() {
        tableExists(false)

        assertThatThrownBy { service.get(LocalDate.of(2026, 9, 26), 5L) }
            .isInstanceOf(ResponseStatusException::class.java)

        verify(jdbc, never()).query(anyString(), any<MapSqlParameterSource>(), anyMapper())
    }

    /** 정렬은 고정이다 — 사용자 입력이 ORDER BY 로 들어갈 경로가 없다. */
    @Test fun orderingIsFixedToIdxDesc() {
        tableExists(true)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))).thenReturn(1L)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper()))
            .thenReturn(java.util.List.of())

        service.search(form(LocalDate.of(2026, 9, 26)),
            PageRequest.of(0, 20, Sort.by("servicename")))

        val sql = argumentCaptor<String>()
        verify(jdbc).query(sql.capture(), any<MapSqlParameterSource>(), anyMapper())
        assertThat(sql.firstValue).contains("ORDER BY IDX DESC").doesNotContain("CREATEDTIME DESC")
        assertThat(sql.firstValue).doesNotContain("servicename DESC")
    }

    companion object {
        private fun row(idx: Long, op: String?, servicename: String?, userid: String?): FidoLogRow =
            row(idx, op, servicename, userid, "Success")

        private fun row(idx: Long, op: String?, servicename: String?, userid: String?, status: String?): FidoLogRow =
            row(idx, op, servicename, userid, status, 2L)

        private fun row(idx: Long, op: String?, servicename: String?, userid: String?, status: String?, bioType: Long?): FidoLogRow =
            FidoLogRow(idx, 1L, op, servicename, userid, bioType, status, LocalDateTime.of(2026, 9, 26, 10, 0))

        private fun base64url(json: String): String =
            java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
    }

    /** 조건이 없으면 DB 가 한 페이지만 읽는다. 행마다 JSONDATA 를 풀어 구분·서비스명·사용자·인증장치를 채운다. */
    @Test fun withoutFiltersPagesInDbAndDecodesEachRow() {
        tableExists(true)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))).thenReturn(1L)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(listOf())

        service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20))

        val sql = argumentCaptor<String>()
        val mapper = argumentCaptor<RowMapper<Any>>()
        verify(jdbc).query(sql.capture(), any<MapSqlParameterSource>(), mapper.capture())
        assertThat(sql.firstValue).contains("JSONDATA").contains("RN > :offset").doesNotContain("JSON_VALUE")

        val rs: ResultSet = mock()
        whenever(rs.getLong("IDX")).thenReturn(7L)
        whenever(rs.getLong("COMPANY_IDX")).thenReturn(1L)
        whenever(rs.getString("SERVICENAME")).thenReturn("SERVICE")
        whenever(rs.getTimestamp("CREATEDTIME")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 10, 2, 14, 42, 20)))
        whenever(rs.getString("JSONDATA")).thenReturn(base64url(
            "{\"transaction\":{\"serviceName\":\"com.kbstar.kbbank\",\"userName\":\"u1\",\"op\":\"TC\",\"bioType\":2,\"status\":\"Error\"}}"))
        val r = mapper.firstValue.mapRow(rs, 0) as FidoLogRow

        assertThat(r.op).isEqualTo("TC")
        assertThat(r.servicename).isEqualTo("com.kbstar.kbbank")
        assertThat(r.userid).isEqualTo("u1")
        assertThat(r.bioTypeLabel()).isEqualTo("지문")
        assertThat(r.status).isEqualTo("Error")
    }

    /** JSON 에 서비스명이 없으면 SERVICENAME 컬럼 값을 쓴다. */
    @Test fun servicenameFallsBackToColumn() {
        tableExists(true)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))).thenReturn(1L)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(listOf())
        service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20))
        val mapper = argumentCaptor<RowMapper<Any>>()
        verify(jdbc).query(anyString(), any<MapSqlParameterSource>(), mapper.capture())

        val rs: ResultSet = mock()
        whenever(rs.getString("SERVICENAME")).thenReturn("kbstar")
        whenever(rs.getString("JSONDATA")).thenReturn("garbage!!")
        val r = mapper.firstValue.mapRow(rs, 0) as FidoLogRow

        assertThat(r.servicename).isEqualTo("kbstar")
        assertThat(r.op).isNull()
        assertThat(r.bioTypeLabel()).isEqualTo("알수없음")
    }

    /** 공백뿐인 검색어는 조건이 아니다 — 하루치를 훑지 않고 DB 페이징으로 간다. */
    @Test fun blankTermsDoNotTriggerScan() {
        tableExists(true)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))).thenReturn(1L)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(listOf())
        val f = form(LocalDate.of(2026, 9, 26))
        f.servicename = "  "; f.userid = " "; f.op = "bogus"

        service.search(f, PageRequest.of(0, 20))

        val sql = argumentCaptor<String>()
        verify(jdbc).query(sql.capture(), any<MapSqlParameterSource>(), anyMapper())
        assertThat(sql.firstValue).contains("RN > :offset").doesNotContain(":cap")
    }

    /** 조건이 있으면 그날 그 고객사 로그를 풀어 Java 에서 거르고 페이지를 나눈다. */
    @Test fun filtersScanTheDayAndMatchInJava() {
        tableExists(true)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(listOf(
            row(5, "TC", "com.kbstar.kbbank", "f5JyUa2Q"),
            row(4, "Auth", "com.kbstar.kbbank", "f5JyUa2Q"),
            row(3, "TC", "com.kbstar.kbbiz", "113057331000001_0_*com.kbstar.kbbiz_*KF"),
            row(2, "tc", "com.kbstar.kbbank", "F5JYUA2Q-other"),
            row(1, null, null, null)))
        val f = form(LocalDate.of(2026, 9, 26))
        f.op = "TC"; f.servicename = "KBBANK"; f.userid = "f5jyua2q"

        val result = service.search(f, PageRequest.of(0, 1))

        assertThat(result.truncated).isFalse()
        assertThat(result.page.totalElements).isEqualTo(2)
        assertThat(result.page.content).extracting(idxOf()).containsExactly(5L)
        val sql = argumentCaptor<String>()
        val params = argumentCaptor<MapSqlParameterSource>()
        verify(jdbc).query(sql.capture(), params.capture(), anyMapper())
        assertThat(sql.firstValue).contains("FIDO_LOGS_20260926").contains("COMPANY_IDX = :companyIdx")
            .contains("rownum <= :cap").contains("ORDER BY IDX DESC").doesNotContain("CREATEDTIME DESC")
        assertThat(params.firstValue.getValue("companyIdx")).isEqualTo(1L)
        assertThat(params.firstValue.getValue("cap")).isEqualTo(FidoLogQueryService.SCAN_LIMIT + 1)
        verify(jdbc, never()).queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))
    }

    /**
     * 조건 검색은 그날 로그를 최대 SCAN_LIMIT 건 풀어야 한다. 같은 조건으로 페이지만 넘기면 그 결과를 다시 쓴다
     * (최대 30초). 조건 문자열의 대소문자만 다르면 같은 결과다(비교가 대소문자를 무시한다).
     */
    @Test fun filterResultIsReusedAcrossPages() {
        tableExists(true)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(listOf(
            row(5, "TC", "s", "u"), row(4, "TC", "s", "u"), row(3, "TC", "s", "u")))
        val f = form(LocalDate.of(2026, 9, 26))
        f.op = "TC"
        val sameButLower = form(LocalDate.of(2026, 9, 26))
        sameButLower.op = "tc"

        val first = service.search(f, PageRequest.of(0, 2))
        val second = service.search(sameButLower, PageRequest.of(1, 2))

        assertThat(first.page.content).extracting(idxOf()).containsExactly(5L, 4L)
        assertThat(second.page.content).extracting(idxOf()).containsExactly(3L)
        assertThat(second.page.totalElements).isEqualTo(3)
        verify(jdbc, times(1)).query(anyString(), any<MapSqlParameterSource>(), anyMapper())
    }

    /** 조건이 다르거나 고객사가 다르면 다시 읽는다. */
    @Test fun filterResultIsKeyedByConditionsAndTenant() {
        tableExists(true)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(listOf(row(5, "TC", "s", "u")))
        val tc = form(LocalDate.of(2026, 9, 26))
        tc.op = "TC"
        val auth = form(LocalDate.of(2026, 9, 26))
        auth.op = "Auth"

        service.search(tc, PageRequest.of(0, 20))
        service.search(auth, PageRequest.of(0, 20))
        val other = ManagerUserDetails(3L, "other", null, "O", 2L, "O", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(other, null, other.authorities)
        service.search(tc, PageRequest.of(0, 20))

        verify(jdbc, times(3)).query(anyString(), any<MapSqlParameterSource>(), anyMapper())
    }

    /** 조건 없는 목록은 본문을 매번 읽고 COUNT(*) 만 다시 쓴다. */
    @Test fun plainListReusesCountOnly() {
        tableExists(true)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))).thenReturn(40L)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(listOf(row(5, "TC", "s", "u")))

        service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(0, 20))
        val second = service.search(form(LocalDate.of(2026, 9, 26)), PageRequest.of(1, 20))

        assertThat(second.page.totalElements).isEqualTo(40L)
        verify(jdbc, times(1)).queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))
        verify(jdbc, times(2)).query(anyString(), any<MapSqlParameterSource>(), anyMapper())
    }

    /** 하루 로그가 상한을 넘으면 앞부분만 보고, 그 사실을 화면에 알린다. */
    @Test fun scanStopsAtLimitAndReportsTruncation() {
        tableExists(true)
        val many: List<FidoLogRow> = (0 until FidoLogQueryService.SCAN_LIMIT + 1).map { i -> row(i.toLong(), "Auth", "s", "u") }
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(many)
        val f = form(LocalDate.of(2026, 9, 26))
        f.op = "Auth"

        val result = service.search(f, PageRequest.of(0, 20))

        assertThat(result.truncated).isTrue()
        assertThat(result.page.totalElements).isEqualTo(FidoLogQueryService.SCAN_LIMIT.toLong())
    }

    /** 고객이 정상 진행했는지 보려고 상태로 거른다. 대소문자는 가리지 않는다. */
    @Test fun statusFilterMatchesIgnoringCase() {
        tableExists(true)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(listOf(
            row(3, "Auth", "s", "u", "Error"),
            row(2, "Auth", "s", "u", "Success"),
            row(1, "Auth", "s", "u", "error")))
        val f = form(LocalDate.of(2026, 9, 26))
        f.status = "Error"

        val result = service.search(f, PageRequest.of(0, 20))

        assertThat(result.page.content).extracting(idxOf()).containsExactly(3L, 1L)
    }

    /** 인증장치는 JSONDATA 안의 값이라 그날 로그를 풀어 코드로 거른다. */
    @Test fun bioTypeFilterMatchesCode() {
        tableExists(true)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(listOf(
            row(3, "Auth", "s", "u", "Success", 16L),
            row(2, "TC", "s", "u", "Success", 2L),
            row(1, "Auth", "s", "u", "RequestOK", null)))
        val f = form(LocalDate.of(2026, 9, 26))
        f.bioType = 16L

        val result = service.search(f, PageRequest.of(0, 20))

        assertThat(result.page.content).extracting(idxOf()).containsExactly(3L)
    }

    /** 번호는 컬럼이라 JSONDATA 를 풀지 않고 SQL 로 건다(목록·건수 모두). 값은 바인드 변수로만 간다. */
    @Test fun idxFilterIsAppliedInSql() {
        tableExists(true)
        whenever(jdbc.queryForObject(anyString(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))).thenReturn(1L)
        whenever(jdbc.query(anyString(), any<MapSqlParameterSource>(), anyMapper())).thenReturn(listOf())
        val f = form(LocalDate.of(2026, 9, 26))
        f.idx = 914018809L

        service.search(f, PageRequest.of(0, 20))

        val countSql = argumentCaptor<String>()
        verify(jdbc).queryForObject(countSql.capture(), any<MapSqlParameterSource>(), eq(Long::class.javaObjectType))
        assertThat(countSql.firstValue).contains("AND IDX = :idx")
        val sql = argumentCaptor<String>()
        val params = argumentCaptor<MapSqlParameterSource>()
        verify(jdbc).query(sql.capture(), params.capture(), anyMapper())
        assertThat(sql.firstValue).contains("COMPANY_IDX = :companyIdx AND IDX = :idx").doesNotContain("914018809")
        assertThat(params.firstValue.getValue("idx")).isEqualTo(914018809L)
    }
}
