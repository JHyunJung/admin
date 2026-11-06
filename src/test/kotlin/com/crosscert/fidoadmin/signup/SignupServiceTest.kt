package com.crosscert.fidoadmin.signup

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.manager.service.ManagerUserIdGuard
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.core.context.SecurityContextHolder

class SignupServiceTest {

    val managers: CcfaManagerRepository = mock(CcfaManagerRepository::class.java)
    val em: EntityManager = mock(EntityManager::class.java)
    val lockQuery: Query = mock(Query::class.java)
    val audit: AuditLogger = mock(AuditLogger::class.java)
    val expiry: PasswordExpiryPolicy = mock(PasswordExpiryPolicy::class.java)
    val companies: CcfaCompanyRepository = mock(CcfaCompanyRepository::class.java)
    val service = SignupService(managers, companies, ManagerUserIdGuard(managers, em), audit, expiry)

    @BeforeEach fun stubLock() {
        // 가입은 인증 없이 일어난다. 테스트도 로그인 사용자가 없는 상태로 돌린다.
        SecurityContextHolder.clearContext()
        whenever(em.createNativeQuery(anyString())).thenReturn(lockQuery)
        whenever(lockQuery.executeUpdate()).thenReturn(0)
        whenever(managers.save(any<CcfaManager>())).thenAnswer { it.getArgument<CcfaManager>(0) }
    }

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    /** 신청 결과는 항상 승인대기 + 미배정 소속이다. */
    @Test fun applyForcesPendingStatusAndUnassignedCompany() {
        whenever(managers.findByUserId("newbie")).thenReturn(Optional.empty())

        val saved = service.apply("newbie", "hash", "홍길동",
            "hong@kb.local", "010-0000-0000", "업무 담당자입니다")

        assertThat(saved.status).isEqualTo("승인대기")
        assertThat(saved.companyIdx).isEqualTo(-1L)
        assertThat(saved.userId).isEqualTo("newbie")
        assertThat(saved.userPw).isEqualTo("hash")
        assertThat(saved.userNm).isEqualTo("홍길동")
        assertThat(saved.userEmail).isEqualTo("hong@kb.local")
        assertThat(saved.userPhone).isEqualTo("010-0000-0000")
        assertThat(saved.etc).contains("업무 담당자입니다")
        assertThat(saved.createdtime).isNotNull()
        assertThat(saved.updatedtime).isNotNull()
    }

    /**
     * 상태·소속을 넘길 수 있는 파라미터가 존재하면 안 된다. 서비스가 강제한다는 보장은
     * "덮어쓴다"가 아니라 "받을 통로가 없다"여야 한다. 시그니처를 리플렉션으로 고정한다.
     */
    @Test fun applyHasNoParameterForStatusOrCompanyIdx() {
        val methods = SignupService::class.java.methods.filter { it.name == "apply" }
        assertThat(methods).hasSize(1)
        assertThat(methods[0].parameterTypes)
            .containsOnly(String::class.java)   // Long(소속)·상태 열거형이 끼어들 여지가 없다
        assertThat(methods[0].parameterCount).isEqualTo(6)
    }

    /**
     * SignupService 는 CrudService 를 상속하면 안 된다.
     * CrudService.create() 는 TenantContext.require() 로 이어져 로그인 사용자를 요구하는데,
     * 가입은 인증 없이 호출된다.
     */
    @Test fun signupServiceDoesNotExtendCrudService() {
        assertThat(CrudService::class.java.isAssignableFrom(SignupService::class.java)).isFalse()
    }

    /** IDX 는 시퀀스가 채번한다. 서비스가 직접 넣으면 안 된다. */
    @Test fun applyDoesNotAssignIdx() {
        whenever(managers.findByUserId("newbie")).thenReturn(Optional.empty())
        val saved = service.apply("newbie", "hash", "홍길동", "hong@kb.local", null, null)
        assertThat(saved.idx).isNull()
    }

    /** 신청 사유가 비어 있으면 ETC 에 빈 접두사만 남기지 않는다. */
    @Test fun applyLeavesEtcNullWhenReasonIsBlank() {
        whenever(managers.findByUserId("newbie")).thenReturn(Optional.empty())
        val saved = service.apply("newbie", "hash", "홍길동", "hong@kb.local", null, "   ")
        assertThat(saved.etc).isNull()
    }

    @Test fun applyRejectsDuplicateUserId() {
        val existing = CcfaManager()
        existing.userId = "kbadmin"
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(existing))

        assertThatThrownBy { service.apply("kbadmin", "hash", "홍길동", "hong@kb.local", null, null) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .hasMessageContaining("kbadmin")
        verify(managers, never()).save(any<CcfaManager>())
    }

    /**
     * USER_ID 에 DB 유니크 제약이 없어(스키마 변경 불가) 동시 신청이 둘 다
     * findByUserId() 에서 빈 결과를 볼 수 있다. 존재 검사 전에 테이블을 배타 잠금해
     * 직렬화해야 한다(ManagerService.insert() 와 같은 방식).
     */
    @Test fun applyLocksTableBeforeDuplicateCheck() {
        whenever(managers.findByUserId("newbie")).thenReturn(Optional.empty())

        service.apply("newbie", "hash", "홍길동", "hong@kb.local", null, null)

        val order = Mockito.inOrder(em, lockQuery, managers)
        order.verify(em).createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE")
        order.verify(lockQuery).executeUpdate()
        order.verify(managers).findByUserId("newbie")
        order.verify(managers).save(any<CcfaManager>())
    }

    @Test fun existsUserIdReflectsRepository() {
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(CcfaManager()))
        whenever(managers.findByUserId("newbie")).thenReturn(Optional.empty())
        assertThat(service.existsUserId("kbadmin")).isTrue()
        assertThat(service.existsUserId("newbie")).isFalse()
    }
}
