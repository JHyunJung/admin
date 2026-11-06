package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo
import com.crosscert.fidoadmin.system.service.SystemInfoService
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * AssignedIdCrudService 가 실제 Oracle 에서 기존 행을 덮어쓰지 않는지 확인한다.
 * save() 였다면 'VERSION' 등록이 MERGE 로 조용히 성공해 PROP_VALUE 가 바뀌었을 것이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(SystemInfoService::class, TenantContext::class, SelectedTenant::class)
class AssignedIdInsertIntegrationTest : OracleContainerSupport() {

    @Autowired lateinit var em: EntityManager
    @Autowired lateinit var service: SystemInfoService
    @MockitoBean lateinit var audit: AuditLogger

    @BeforeEach
    fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    @AfterEach
    fun clear() { SecurityContextHolder.clearContext() }

    private fun info(key: String, value: String): CcfaSystemInfo { val i = CcfaSystemInfo(); i.propKey = key; i.propValue = value; return i }

    @Test
    fun existingKeyIsRejectedAndRowUntouched() {
        assertThatThrownBy { service.create(info("VERSION", "9.9.9")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        em.clear()
        assertThat(em.find(CcfaSystemInfo::class.java, "VERSION").propValue).isEqualTo("1.0.0") // 시드 값 그대로
    }

    @Test
    fun newKeyIsInsertedAndReadable() {
        val saved = service.create(info("IT_NEW_KEY", "v"))
        assertThat(saved.updatedtime).isNotNull()
        em.clear()
        val found: CcfaSystemInfo? = em.find(CcfaSystemInfo::class.java, "IT_NEW_KEY")
        assertThat(found).isNotNull()
        assertThat(found!!.propValue).isEqualTo("v")
    }
}
