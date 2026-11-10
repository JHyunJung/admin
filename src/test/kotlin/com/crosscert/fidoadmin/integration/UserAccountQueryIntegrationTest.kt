package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.fido.entity.Userinfo
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository
import com.crosscert.fidoadmin.fido.service.UserAccountRow
import jakarta.persistence.EntityManager
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.test.context.ActiveProfiles

/**
 * 사용자 단위 목록 질의(`UserinfoRepository`)를 실제 Oracle 에 실행해 현재 동작을 고정한다.
 *
 * 이 리포지터리는 CrudService 를 거치지 않아 테넌트 경계를 질의문이 직접 진다. 단위 테스트는
 * 목으로 인자만 확인하고, 네이티브 건수 질의는 기동 시 문법 검증도 받지 않으므로
 * 테넌트 격리·부분 일치·건수 일치·집계·정렬은 여기서만 확인된다.
 *
 * 시드(COMPANY_IDX 1·2)와 섞이지 않도록 쓰지 않는 고객사 번호를 쓴다. `@DataJpaTest` 가 롤백한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserAccountQueryIntegrationTest : OracleContainerSupport() {

    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var users: UserinfoRepository

    private val tenantA = 990_001L
    private val tenantB = 990_002L

    private val jan1 = LocalDateTime.of(2026, 1, 1, 10, 0)
    private val jan2 = LocalDateTime.of(2026, 1, 2, 10, 0)
    private val jan3 = LocalDateTime.of(2026, 1, 3, 10, 0)
    private val jan5 = LocalDateTime.of(2026, 1, 5, 10, 0)

    @BeforeEach
    fun seed() {
        // 고객사 A: (Alice, svcA) 기기 2대(정상 1·중지 1), (alice, svcB) 1대, (bob, svcA) 1대
        users.save(device(tenantA, "Alice", "svcA", "O", jan1))
        users.save(device(tenantA, "Alice", "svcA", "X", jan3))
        users.save(device(tenantA, "alice", "svcB", "O", jan2))
        users.save(device(tenantA, "bob", "svcA", "O", jan3))
        // 고객사 B: A 와 같은 (Alice, svcA) — A 의 결과에 섞이면 안 된다
        users.save(device(tenantB, "Alice", "svcA", "O", jan5))
        users.flush()
        em.clear()
    }

    @Test
    fun listGroupsByUseridAndServicenameWithAggregatesAndOrder() {
        val rows = users.findUserAccounts(tenantA, null, null, Pageable.unpaged())

        // 최근 등록 역순, 같으면 userid 오름차순
        assertThat(rows).containsExactly(
            UserAccountRow("Alice", "svcA", 2L, 1L, jan3),
            UserAccountRow("bob", "svcA", 1L, 1L, jan3),
            UserAccountRow("alice", "svcB", 1L, 1L, jan2),
        )
        assertThat(users.countUserAccounts(tenantA, null, null)).isEqualTo(rows.size.toLong())
    }

    @Test
    fun otherTenantRowsAreNotMixed() {
        val rows = users.findUserAccounts(tenantB, null, null, Pageable.unpaged())

        assertThat(rows).containsExactly(UserAccountRow("Alice", "svcA", 1L, 1L, jan5))
        assertThat(users.countUserAccounts(tenantB, null, null)).isEqualTo(1L)

        assertThat(users.findByCompanyIdxAndUseridAndServicenameOrderByRegtimeDesc(tenantB, "Alice", "svcA"))
            .extracting<Long?> { it.companyIdx }.containsExactly(tenantB)
    }

    @Test
    fun unknownTenantSeesNothing() {
        assertThat(users.findUserAccounts(990_099L, null, null, Pageable.unpaged())).isEmpty()
        assertThat(users.countUserAccounts(990_099L, null, null)).isZero()
    }

    @Test
    fun useridIsCaseInsensitivePartialMatchAndCountAgrees() {
        val rows = users.findUserAccounts(tenantA, "LIC", null, Pageable.unpaged())

        assertThat(rows).extracting<String?> { it.userid + "/" + it.servicename }
            .containsExactly("Alice/svcA", "alice/svcB")
        assertThat(users.countUserAccounts(tenantA, "LIC", null)).isEqualTo(2L)
    }

    @Test
    fun servicenameIsCaseInsensitivePartialMatchAndCountAgrees() {
        val rows = users.findUserAccounts(tenantA, null, "SVCB", Pageable.unpaged())

        assertThat(rows).extracting<String?> { it.userid + "/" + it.servicename }
            .containsExactly("alice/svcB")
        assertThat(users.countUserAccounts(tenantA, null, "SVCB")).isEqualTo(1L)
    }

    @Test
    fun bothConditionsCombineWithAnd() {
        val rows = users.findUserAccounts(tenantA, "a", "svca", Pageable.unpaged())

        assertThat(rows).extracting<String?> { it.userid + "/" + it.servicename }
            .containsExactly("Alice/svcA")
        assertThat(users.countUserAccounts(tenantA, "a", "svca")).isEqualTo(1L)
    }

    @Test
    fun pageSlicesGroupsWhileCountStaysTotal() {
        val page0 = users.findUserAccounts(tenantA, null, null, PageRequest.of(0, 2))
        val page1 = users.findUserAccounts(tenantA, null, null, PageRequest.of(1, 2))

        assertThat(page0).extracting<String?> { it.userid + "/" + it.servicename }
            .containsExactly("Alice/svcA", "bob/svcA")
        assertThat(page1).extracting<String?> { it.userid + "/" + it.servicename }
            .containsExactly("alice/svcB")
        assertThat(users.countUserAccounts(tenantA, null, null)).isEqualTo(3L)
    }

    @Test
    fun credentialsNeedBothKeysAndStayInTenant() {
        val devices = users.findByCompanyIdxAndUseridAndServicenameOrderByRegtimeDesc(tenantA, "Alice", "svcA")

        assertThat(devices).extracting<LocalDateTime?> { it.regtime }.containsExactly(jan3, jan1)
        assertThat(devices).allSatisfy { assertThat(it.companyIdx).isEqualTo(tenantA) }
        // userid 가 같아도 대소문자가 다르면 다른 사용자다(정확 일치)
        assertThat(users.findByCompanyIdxAndUseridAndServicenameOrderByRegtimeDesc(tenantA, "alice", "svcA")).isEmpty()
    }

    private fun device(companyIdx: Long, userid: String, servicename: String, status: String, regtime: LocalDateTime) =
        Userinfo().apply {
            this.companyIdx = companyIdx
            this.userid = userid
            this.servicename = servicename
            this.aaid = "0012#0001"
            this.authenticatorversion = 1L
            this.signcounter = 0L
            this.status = status
            this.regtime = regtime
            this.createdtime = regtime
        }
}
