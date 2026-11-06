package com.crosscert.fidoadmin.integration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.crosscert.fidoadmin.fido.entity.Sign
import com.crosscert.fidoadmin.fido.repository.SignRepository
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import java.util.Locale
import java.util.function.Function
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.test.context.ActiveProfiles

/**
 * 목록 조회가 CLOB 컬럼을 SELECT 절에서 실제로 빼는지 실제 Oracle 로 확인한다.
 * 단위 테스트는 "findAllExcluding 으로 갔는가" 까지만 보고, 생성된 SQL 과 되돌아온 값은 여기서 본다.
 *
 * EntityBootTest 처럼 로컬 도커 Oracle(local 프로파일)을 쓴다. 질의 모양만 보는 테스트라 시드 데이터에
 * 의존하지 않고, 넣은 행은 @DataJpaTest 트랜잭션이 되돌린다. DB 가 없으면 건너뛴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@EnabledIf("com.crosscert.fidoadmin.LocalOracle#isUp")
class PartialFetchIntegrationTest {

    companion object {
        const val COMPANY = 987_654L
    }

    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var signs: SignRepository

    val sqlLogger: Logger = LoggerFactory.getLogger("org.hibernate.SQL") as Logger
    var previous: Level? = null
    val sql = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun captureSql() {
        previous = sqlLogger.level
        sqlLogger.level = Level.DEBUG
        sql.start()
        sqlLogger.addAppender(sql)
    }

    @AfterEach
    fun stopCapture() {
        sqlLogger.detachAppender(sql)
        sqlLogger.level = previous
    }

    private fun sign(userid: String, plaintext: String, at: LocalDateTime): Sign {
        val s = Sign()
        s.companyIdx = COMPANY; s.userid = userid; s.assertion = "assertion-$userid"
        s.dn = "CN=$userid"; s.plaintext = plaintext; s.data = "data-$userid"; s.signature = "sig-$userid"
        s.memo = "m"; s.del = "N"; s.createtime = at
        em.persist(s)
        return s
    }

    @Test
    fun listReadsEveryColumnExceptTheExcludedClob() {
        val older = sign("u1", "x".repeat(10_000), LocalDateTime.of(2026, 1, 1, 10, 0))
        val newer = sign("u2", "y".repeat(10_000), LocalDateTime.of(2026, 1, 2, 10, 0))
        em.flush(); em.clear()
        sql.list.clear()

        val mine = Specification<Sign> { root, _, cb -> cb.equal(root.get<Long>("companyIdx"), COMPANY) }
        val page: Page<Sign> = signs.findAllExcluding(Sign::class.java, mine,
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createtime", "idx")), setOf("plaintext"))

        // 페이지·정렬·건수는 findAll(spec, pageable) 과 같다.
        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).hasSize(1)
        val row = page.content[0]
        assertThat(row.idx).isEqualTo(newer.idx)
        assertThat(row.userid).isEqualTo("u2")
        assertThat(row.assertion).isEqualTo("assertion-u2")
        assertThat(row.dn).isEqualTo("CN=u2")
        assertThat(row.createtime).isEqualTo(newer.createtime)
        assertThat(row.companyIdx).isEqualTo(COMPANY)
        // 제외한 CLOB 만 비어 있다.
        assertThat(row.plaintext).isNull()

        // 생성된 SELECT 에 PLAINTEXT 가 없다(건수 쿼리는 컬럼을 고르지 않는다).
        val selects = sql.list.joinToString("\n") { it.formattedMessage }.lowercase(Locale.ROOT)
        assertThat(selects).contains("select")
        assertThat(selects).contains("assertion")
        assertThat(selects).doesNotContain("plaintext")
        assertThat(older.idx).isNotNull()
    }

    /** 건수 캐시 경로: 본문만 읽고 COUNT 를 보내지 않는다. 제외 속성이 없으면 엔티티를 그대로 읽는다. */
    @Test
    fun contentOnlyQueryReadsWholeEntitiesWithoutCounting() {
        sign("u1", "p1", LocalDateTime.of(2026, 1, 1, 10, 0))
        val newer = sign("u2", "p2", LocalDateTime.of(2026, 1, 2, 10, 0))
        em.flush(); em.clear()
        sql.list.clear()

        val mine = Specification<Sign> { root, _, cb -> cb.equal(root.get<Long>("companyIdx"), COMPANY) }
        val rows = signs.findContentExcluding(Sign::class.java, mine,
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "idx")), setOf())

        assertThat(rows).extracting(Function<Sign, Long?> { it.idx }).containsExactly(newer.idx)
        assertThat(rows[0].plaintext).isEqualTo("p2")
        val selects = sql.list.joinToString("\n") { it.formattedMessage }.lowercase(Locale.ROOT)
        assertThat(selects).doesNotContain("count(")
    }
}
