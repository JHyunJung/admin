package com.crosscert.fidoadmin.statistics.batch

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.namedparam.SqlParameterSource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * FIDO_STATISTICS 에 한 시간 분량을 쓴다. 서버가 여러 대여도 같은 (회사, 시간)은 한 번만 쌓인다.
 *
 * 스키마에 유니크 제약이 없고 바꿀 수 없어, 테이블을 EXCLUSIVE 로 잠근 뒤 이미 있는 회사를 확인하고
 * 없는 회사 행만 넣는다(`ManagerUserIdGuard` 와 같은 방식). 잠금은 커밋 때 풀린다.
 * EXCLUSIVE 모드는 다른 세션의 SELECT 를 막지 않으므로 대시보드는 기다리지 않는다.
 *
 * JPA 엔티티는 쓰지 않는다. 복합키에 CREATEDTIME 이 있어 저장 용도로 맞지 않다. CREATEDTIME 은 DB 기본값(적재 시각)이다.
 */
@Component
class StatisticsWriter(private val jdbc: NamedParameterJdbcTemplate) {

    /** 범위 안에 이미 적재된 GROUPBY. 고정 길이 문자열이라 사전순 비교가 시간순과 같다. */
    @Transactional(readOnly = true)
    fun loadedGroupbys(from: StatHour, to: StatHour): Set<String> {
        return HashSet(jdbc.queryForList(
            "SELECT DISTINCT GROUPBY FROM FIDO_STATISTICS WHERE GROUPBY >= :from AND GROUPBY <= :to",
            MapSqlParameterSource().addValue("from", from.groupby()).addValue("to", to.groupby()),
            String::class.java))
    }

    /** @return 넣은 행 수. 다른 서버가 먼저 넣은 회사는 건너뛴다 */
    @Transactional
    fun write(hour: StatHour, counts: Map<StatKey, StatCounts>): Int {
        if (counts.isEmpty()) return 0
        val groupby = hour.groupby()
        jdbc.jdbcOperations.execute("LOCK TABLE FIDO_STATISTICS IN EXCLUSIVE MODE")
        val existing: Set<Long> = HashSet(jdbc.queryForList(
            "SELECT DISTINCT COMPANY_IDX FROM FIDO_STATISTICS WHERE GROUPBY = :groupby",
            MapSqlParameterSource("groupby", groupby), Long::class.javaObjectType))

        val rows = ArrayList<SqlParameterSource>()
        val companies = HashSet<Long>()
        counts.forEach { (key, c) ->
            if (existing.contains(key.companyIdx)) return@forEach
            companies.add(key.companyIdx)
            rows.add(MapSqlParameterSource()
                .addValue("companyIdx", key.companyIdx)
                .addValue("serviceName", key.serviceName)
                .addValue("groupby", groupby)
                .addValue("authS", c.authS).addValue("authF", c.authF)
                .addValue("tcS", c.tcS).addValue("tcF", c.tcF)
                .addValue("regS", c.regS).addValue("regF", c.regF)
                .addValue("deregS", c.deregS).addValue("deregF", c.deregF))
        }
        if (existing.isNotEmpty()) {
            log.info("statistics: {} 이미 적재된 회사 {} 건너뜀", groupby, existing)
        }
        if (rows.isEmpty()) return 0
        jdbc.batchUpdate("""
            INSERT INTO FIDO_STATISTICS
              (COMPANY_IDX, SERVICE_NAME, GROUPBY, AUTH_S, AUTH_F, TC_S, TC_F, REG_S, REG_F, DEREG_S, DEREG_F)
            VALUES (:companyIdx, :serviceName, :groupby, :authS, :authF, :tcS, :tcF, :regS, :regF, :deregS, :deregF)
            """.trimIndent() + "\n", rows.toTypedArray())
        log.info("statistics: {} {}개 회사 {}행 적재", groupby, companies.size, rows.size)
        return rows.size
    }

    companion object {
        private val log = LoggerFactory.getLogger(StatisticsWriter::class.java)
    }
}
