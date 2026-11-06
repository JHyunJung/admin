package com.crosscert.fidoadmin.log.service

import com.crosscert.fidoadmin.common.javaTrim
import com.crosscert.fidoadmin.common.javaIsBlank
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.log.web.FidoLogRow
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm
import com.crosscert.fidoadmin.log.web.FidoLogSearchResult
import com.crosscert.fidoadmin.log.web.FidoLogView
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * FIDO 로그 조회. 읽기 전용이다(append-only 로그라 화면에서 고치거나 지우지 않는다).
 *
 * FIDO_LOGS 는 날짜별로 테이블이 갈린다 — `FIDO_LOGS_20260926` 식이다.
 * 테이블 이름이 조회할 때 정해지므로 JPA 엔티티로는 다룰 수 없고, 여기서 JDBC 로 읽는다.
 * 이름을 SQL 에 이어 붙이는 유일한 자리라, [FidoLogTable] 이 날짜를 포맷해 만든
 * 이름만 쓰고 사용자 문자열은 절대 싣지 않는다.
 *
 * `CrudService` 를 상속하지 않는다. 그쪽은 고정된 한 테이블을 JPA 로 다루는
 * 기반이다. 대신 테넌트 경계는 같은 규칙으로 지킨다 — 모든 조회가
 * `COMPANY_IDX = 유효 테넌트` 를 건다.
 */
@Service
class FidoLogQueryService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val tenant: TenantContext,
    private val tables: FidoLogTable,
) {

    /** (고객사, 테이블, 조건) → 조건에 맞은 행 전체. 행 수로 무게를 매겨 메모리 상한을 둔다(행은 작은 값 몇 개). */
    private val scans: Cache<String, Scan> = Caffeine.newBuilder()
        .expireAfterWrite(RESULT_TTL)
        .maximumWeight(4L * SCAN_LIMIT)
        .weigher<String, Scan> { _, v -> v.matched.size + 1 }
        .build()
    /** (고객사, 테이블, 번호 조건) → 총 건수. 조건 없는 목록은 본문은 매번 읽고 건수만 다시 쓴다. */
    private val counts: Cache<String, Long?> = Caffeine.newBuilder()
        .expireAfterWrite(RESULT_TTL).maximumSize(1_000).build()

    private class Scan(val matched: List<FidoLogRow>, val truncated: Boolean)

    /**
     * 목록. 없는 날짜면 빈 페이지다(오류가 아니다 — 그날 로그가 없다는 뜻이다).
     *
     * 운영 JSONDATA 는 base64url 이라 DB 가 안을 볼 수 없다. 번호(IDX)는 컬럼이라 언제나 SQL 로 건다.
     * JSONDATA 조건이 없으면 DB 가 한 페이지만 읽고 그 행만 푼다. 구분·서비스명·사용자·인증장치·상태 조건이 하나라도 있으면 그날 그 고객사 로그를 최근 것부터
     * [SCAN_LIMIT] 건까지 풀어 Java 에서 거른 뒤 페이지를 나눈다.
     *
     * 같은 조건으로 페이지를 넘기면 [RESULT_TTL] 동안 거른 결과(조건 없는 목록은 총 건수)를 다시 쓴다.
     */
    @Transactional(readOnly = true)
    fun search(form: FidoLogSearchForm, pageable: Pageable): FidoLogSearchResult {
        val companyIdx = tenant.companyIdx()
        val table = tables.nameFor(form.logDate)
        if (!tables.exists(table)) {
            return FidoLogSearchResult(PageImpl(listOf(), pageable, 0), false)
        }
        val params = MapSqlParameterSource("companyIdx", companyIdx)
        var where = " WHERE COMPANY_IDX = :companyIdx"
        if (form.idx != null) {
            where += " AND IDX = :idx"
            params.addValue("idx", form.idx)
        }
        // 텍스트 블록을 쓰지 않는다 — 조각을 이어 붙이면 블록마다 들여쓰기가 따로 벗겨져
        // 앞뒤 공백이 사라진다(FROM 테이블명WHERE 처럼 붙어 ORA-03048 이 났다).
        // 시퀀스 PK 역순. PK 인덱스를 거꾸로 읽어 필요한 만큼만 가져온다(CREATEDTIME 정렬은 그날 전체를 정렬한다).
        // 생성 순서와 거의 같다 — 같은 시각에 들어온 행끼리만 순서가 다를 수 있다.
        val select = ("SELECT IDX, COMPANY_IDX, SERVICENAME, CREATEDTIME, JSONDATA FROM " + table
            + where + " ORDER BY IDX DESC")

        val op = opOrNull(form.op)
        val servicename = termOrNull(form.servicename)
        val userid = termOrNull(form.userid)
        val status = termOrNull(form.status)
        val bioType = form.bioType
        // 키에 고객사를 넣는다. 조건이 같아도 고객사가 다르면 다른 결과다.
        val baseKey = "$companyIdx|$table|${form.idx}"
        if (op == null && servicename == null && userid == null && status == null && bioType == null) {
            val countSql = "SELECT COUNT(*) FROM " + table + where
            val total: Long? = counts.get(baseKey) { jdbc.queryForObject(countSql, params, Long::class.javaObjectType) }
            if (total == null || total == 0L) {
                return FidoLogSearchResult(PageImpl(listOf(), pageable, 0), false)
            }
            // Oracle 페이징. rownum 은 정렬 전에 매겨지므로 정렬을 끝낸 결과를 한 번 감싼다.
            params.addValue("offset", pageable.offset).addValue("limit", pageable.pageSize)
            val sql = ("SELECT * FROM (SELECT b.*, rownum AS RN FROM (" + select + ") b"
                + " WHERE rownum <= (:offset + :limit)) WHERE RN > :offset")
            val rows: List<FidoLogRow> = jdbc.query(sql, params) { rs, _ -> toRow(rs) }
            return FidoLogSearchResult(PageImpl(rows, pageable, total), false)
        }

        // 비교가 대소문자를 무시하므로 키도 소문자로 맞춘다.
        val scanKey = (baseKey + "|" + op + "|" + servicename + "|" + userid + "|" + status + "|" + bioType)
            .lowercase(Locale.ROOT)
        val scan = scans.get(scanKey) {
            params.addValue("cap", SCAN_LIMIT + 1)
            val scanned: List<FidoLogRow> = jdbc.query(
                "SELECT * FROM ($select) WHERE rownum <= :cap", params) { rs, _ -> toRow(rs) }
            val cut = scanned.size > SCAN_LIMIT
            val hits = (if (cut) scanned.subList(0, SCAN_LIMIT) else scanned).stream()
                .filter { r -> op == null || op.equals(r.op, ignoreCase = true) }
                .filter { r -> servicename == null || containsIgnoreCase(r.servicename, servicename) }
                .filter { r -> userid == null || containsIgnoreCase(r.userid, userid) }
                .filter { r -> status == null || status.equals(r.status, ignoreCase = true) }
                .filter { r -> bioType == null || bioType == r.bioType }
                .toList()
            Scan(hits, cut)
        }
        val matched = scan.matched
        val truncated = scan.truncated
        val from = Math.min(pageable.offset, matched.size.toLong()).toInt()
        val to = Math.min(from + pageable.pageSize, matched.size)
        return FidoLogSearchResult(PageImpl(matched.subList(from, to), pageable, matched.size.toLong()), truncated)
    }

    /**
     * 상세 한 건. 다른 고객사의 행은 `COMPANY_IDX` 조건에서 걸러져 404 가 된다
     * (있는데 못 본다는 사실조차 알리지 않는다).
     */
    @Transactional(readOnly = true)
    fun get(date: LocalDate?, id: Long?): FidoLogView {
        val companyIdx = tenant.companyIdx()
        val table = tables.nameFor(date)
        if (!tables.exists(table)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "FIDO 로그 $id")
        }

        val sql = ("SELECT IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, CREATEDTIME, JSONDATA "
            + "FROM " + table + " "
            + "WHERE COMPANY_IDX = :companyIdx "
            + "AND IDX = :id")

        val params = MapSqlParameterSource()
            .addValue("companyIdx", companyIdx)
            .addValue("id", id)

        val found: List<FidoLogView> = jdbc.query(sql, params) { rs, _ ->
            FidoLogView.of(
                rs.getLong("IDX"),
                rs.getLong("COMPANY_IDX"),
                rs.getString("SERIALCODE"),
                rs.getString("SERVICENAME"),
                createdtime(rs),
                rs.getString("JSONDATA"))
        }

        if (found.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "FIDO 로그 $id")
        }
        return found[0]
    }

    companion object {
        /**
         * 조건 검색이 한 번에 풀어 보는 최대 행 수. 하루 로그가 이보다 많으면 최근 것부터 이만큼만 보고
         * 화면에 알린다. 행마다 JSONDATA 를 풀지만 목록 행(작은 값 몇 개)만 남기므로 메모리는 행 수에 비례한다.
         */
        const val SCAN_LIMIT = 50_000

        private val OPS: Set<String> = java.util.Set.of("reg", "auth", "dereg", "tc")

        /**
         * 같은 조건으로 페이지를 넘길 때 다시 쓰는 시간. 조건 검색은 매번 그날 로그를 최대 [SCAN_LIMIT] 건
         * 풀어야 해서 비싸다. 이 시간 안에는 새로 쌓인 로그와 "총 N건" 이 늦게 보인다.
         */
        @JvmField
        internal val RESULT_TTL: Duration = Duration.ofSeconds(30)

        private fun createdtime(rs: ResultSet): LocalDateTime? =
            if (rs.getTimestamp("CREATEDTIME") == null) null else rs.getTimestamp("CREATEDTIME").toLocalDateTime()

        private fun toRow(rs: ResultSet): FidoLogRow =
            FidoLogRow.of(rs.getLong("IDX"), rs.getLong("COMPANY_IDX"), rs.getString("SERVICENAME"),
                createdtime(rs), FidoLogPayload.parse(rs.getString("JSONDATA")))

        /** 아는 구분만 조건이 된다. 그 밖(빈 값 포함)은 조건 없음. */
        private fun opOrNull(op: String?): String? =
            if (op != null && OPS.contains(op.javaTrim().lowercase(Locale.ROOT))) op.javaTrim() else null

        private fun termOrNull(value: String?): String? =
            if (value == null || value.javaIsBlank()) null else value.javaTrim()

        private fun containsIgnoreCase(value: String?, term: String): Boolean =
            value != null && value.lowercase(Locale.ROOT).contains(term.lowercase(Locale.ROOT))
    }
}
