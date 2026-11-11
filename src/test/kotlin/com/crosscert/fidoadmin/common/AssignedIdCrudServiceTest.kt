package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import jakarta.persistence.Query
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class AssignedIdCrudServiceTest {

    interface InfoRepo : AdminRepository<CcfaSystemInfo, String>

    val repo: InfoRepo = mock()
    val audit: AuditLogger = mock()
    val em: EntityManager = mock()
    val lockQuery: Query = mock()
    val tenant = TenantContext(SelectedTenant())

    val service: AssignedIdCrudService<CcfaSystemInfo, String, SearchForm> =
        object : AssignedIdCrudService<CcfaSystemInfo, String, SearchForm>(repo, audit, em, tenant) {
            override fun toSpecification(form: SearchForm): Specification<CcfaSystemInfo>? = null
            override fun companyIdxAttribute(): String? = null
            override fun companyIdxOf(entity: CcfaSystemInfo): Long? = null
            override fun setCompanyIdx(entity: CcfaSystemInfo, companyIdx: Long?) {}
            override fun idOf(entity: CcfaSystemInfo): String? = entity.propKey
            override fun tableName(): String = "CCFA_SYSTEM_INFO"
            override fun assignedId(entity: CcfaSystemInfo): String? = entity.propKey
        }

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        whenever(em.createNativeQuery(Mockito.anyString())).thenReturn(lockQuery)
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun info(key: String?): CcfaSystemInfo { val i = CcfaSystemInfo(); i.propKey = key; i.propValue = "v"; return i }

    /** 핵심: 기존 행이 있으면 save() 의 MERGE 로 덮어쓰지 않고 거부한다. */
    @Test fun createRejectsExistingKeyWithoutTouchingRow() {
        whenever(repo.existsById("VERSION")).thenReturn(true)
        assertThatThrownBy { service.create(info("VERSION")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .hasMessageContaining("VERSION")
        verify(em, never()).persist(anyOrNull())
        verify(repo, never()).save(anyOrNull<CcfaSystemInfo>())
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    @Test fun createPersistsNewKeyAndAudits() {
        whenever(repo.existsById("NEW_KEY")).thenReturn(false)
        val saved = service.create(info("NEW_KEY"))
        assertThat(saved.propKey).isEqualTo("NEW_KEY")
        verify(em).persist(saved)
        verify(em).flush()
        verify(repo, never()).save(anyOrNull<CcfaSystemInfo>())
        verify(audit).logGlobal(AuditType.CREATE, "CCFA_SYSTEM_INFO CREATE NEW_KEY")
    }

    @Test fun createRejectsBlankKey() {
        assertThatThrownBy { service.create(info(null)) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.create(info("  ")) }.isInstanceOf(IllegalArgumentException::class.java)
        verify(em, never()).persist(anyOrNull())
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    /** 존재 검사와 INSERT 사이에 다른 세션이 같은 키를 넣은 경우(경쟁) 도 같은 예외로 화면에 전달된다. */
    @Test fun persistFailureBecomesDataIntegrityViolation() {
        whenever(repo.existsById("RACE")).thenReturn(false)
        Mockito.doThrow(PersistenceException("ORA-00001")).`when`(em).flush()
        assertThatThrownBy { service.create(info("RACE")) }.isInstanceOf(DataIntegrityViolationException::class.java)
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    /** update 는 기존 경로(get → mutator → save) 그대로다. */
    @Test fun updateStillUsesSave() {
        whenever(repo.findById("VERSION")).thenReturn(Optional.of(info("VERSION")))
        whenever(repo.save(any<CcfaSystemInfo>())).thenAnswer { inv -> inv.getArgument<CcfaSystemInfo>(0) }
        val out = service.update("VERSION") { i -> i.propValue = "2" }
        assertThat(out.propValue).isEqualTo("2")
        verify(repo).save(anyOrNull<CcfaSystemInfo>())
        verify(em, never()).persist(anyOrNull())
    }

    /**
     * PK 제약이 없는 테이블에서는 존재 검사만으로 동시 등록을 막을 수 없다.
     * LOCK TABLE 을 존재 검사보다 먼저 실행해 이후 요청을 직렬화해야 한다.
     */
    @Test fun locksTableBeforeExistenceCheck() {
        whenever(repo.existsById("NEW_KEY")).thenReturn(false)
        service.create(info("NEW_KEY"))

        val order = Mockito.inOrder(em, repo)
        order.verify(em).createNativeQuery("LOCK TABLE CCFA_SYSTEM_INFO IN EXCLUSIVE MODE")
        order.verify(repo).existsById("NEW_KEY")
        verify(lockQuery).executeUpdate()
    }

    @Test fun duplicateAfterLockStillDoesNotPersist() {
        whenever(repo.existsById("VERSION")).thenReturn(true)
        assertThatThrownBy { service.create(info("VERSION")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        verify(em).createNativeQuery("LOCK TABLE CCFA_SYSTEM_INFO IN EXCLUSIVE MODE")
        verify(lockQuery).executeUpdate()
        verify(em, never()).persist(anyOrNull())
    }
}
