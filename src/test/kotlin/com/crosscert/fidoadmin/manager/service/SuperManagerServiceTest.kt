package com.crosscert.fidoadmin.manager.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.LoginAttemptService
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.common.TenantMismatchException
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.manager.web.ManagerSearchForm
import com.crosscert.fidoadmin.signup.SignupPolicy
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class SuperManagerServiceTest {

    val managers: CcfaManagerRepository = mock(CcfaManagerRepository::class.java)
    val policies: CcfaManagerPwPolicyRepository = mock(CcfaManagerPwPolicyRepository::class.java)
    val loginAttempts: LoginAttemptService = mock(LoginAttemptService::class.java)
    val audit: AuditLogger = mock(AuditLogger::class.java)
    val em: EntityManager = mock(EntityManager::class.java)
    val lockQuery: Query = mock(Query::class.java)
    val expiry: PasswordExpiryPolicy = mock(PasswordExpiryPolicy::class.java)
    val selected = SelectedTenant()
    val tenant = TenantContext(selected)
    val userIdGuard = ManagerUserIdGuard(managers, em)
    val service = SuperManagerService(managers, audit, policies, loginAttempts, em, tenant, userIdGuard, expiry)

    private fun loginSuperSelecting(companyIdx: Long) {
        val u = ManagerUserDetails(9L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        whenever(em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE")).thenReturn(lockQuery)
        selected.select(companyIdx)
    }

    private fun loginSuperAs(idx: Long) {
        val u = ManagerUserDetails(idx, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        whenever(em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE")).thenReturn(lockQuery)
    }

    private fun loginCompany(companyIdx: Long) {
        val u = ManagerUserDetails(3L, "bizuser", null, "고객사담당", companyIdx, "고객사", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    /** companyIdx 는 이 화면이 소유한 행의 COMPANY_IDX 다(테넌트 운영자면 5L, 슈퍼관리자면 0L). */
    private fun manager(idx: Long?, companyIdx: Long?): CcfaManager {
        val m = CcfaManager(); m.idx = idx; m.userId = "u$idx"; m.userPw = "hash"; m.companyIdx = companyIdx
        return m
    }

    private fun manager(idx: Long?, companyIdx: Long?, userId: String): CcfaManager {
        val m = manager(idx, companyIdx)
        m.userId = userId
        return m
    }

    /** 이 화면은 유효 테넌트가 아니라 상수 0 으로 건다. */
    @Test fun 슈퍼관리자_계정만_조회된다() {
        loginSuperSelecting(9L)
        val captor = argumentCaptor<Specification<CcfaManager>>()
        service.search(ManagerSearchForm(), PageRequest.of(0, 20, Sort.by("idx")))
        verify(managers).findAll(captor.capture(), any<PageRequest>())
        assertThat(companyIdxEqualsIn(captor.firstValue)).isEqualTo(0L)
    }

    @Test fun 등록하면_COMPANY_IDX_가_0_이_된다() {
        loginSuperSelecting(9L)
        whenever(managers.findByUserId(anyOrNull())).thenReturn(Optional.empty())
        whenever(managers.save(any<CcfaManager>())).thenAnswer { it.getArgument<CcfaManager>(0) }
        val created = service.create(manager(null, 5L))
        assertThat(created.companyIdx).isEqualTo(0L)
    }

    /** 테넌트 운영자 행은 이 화면에서 열 수 없다. */
    @Test fun 일반_운영자_행은_열_수_없다() {
        loginSuperSelecting(9L)
        whenever(managers.findById(1L)).thenReturn(Optional.of(manager(1L, 5L)))
        assertThatThrownBy { service.get(1L) }.isInstanceOf(TenantMismatchException::class.java)
    }

    @Test fun 자기_자신은_삭제할_수_없다() {
        loginSuperAs(1L)
        whenever(managers.findById(1L)).thenReturn(Optional.of(manager(1L, 0L)))
        assertThatThrownBy { service.delete(1L) }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("자기 자신")
        verify(managers, never()).delete(any<CcfaManager>())
    }

    /** COMPANY 역할이 서비스로 직접 부르면(URL 설정이 없다고 가정해도) 거부되어야 한다. */
    @Test fun COMPANY_역할은_서비스_계층에서_거부된다() {
        loginCompany(5L)
        assertThatThrownBy { service.search(ManagerSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))) }
            .isInstanceOf(AccessDeniedException::class.java)
    }

    /** 미선택 SUPER 도 이 화면을 열 수 있다 — companyIdxAttribute() 가 null 이라 유효 테넌트를 요구하지 않는다. */
    @Test fun 미선택_SUPER_도_조회할_수_있다() {
        loginSuperAs(1L) // selected 테넌트 없음
        whenever(managers.findAll(any<Specification<CcfaManager>>(), any<PageRequest>()))
            .thenReturn(PageImpl(listOf()))
        service.search(ManagerSearchForm(), PageRequest.of(0, 20, Sort.by("idx")))
        // 예외 없이 통과하면 유효 테넌트를 요구하지 않았다는 뜻이다.
    }

    /**
     * USER_ID 에 유니크 제약이 없어 코드에서 중복을 막는다. 이 화면으로 이미 존재하는
     * USER_ID(슈퍼관리자 계정의 것)를 다시 등록하려 하면 거부된다.
     */
    @Test fun duplicateUserIdIsRejectedBeforeSave() {
        loginSuperSelecting(9L)
        whenever(managers.findByUserId("superadmin")).thenReturn(Optional.of(manager(2L, 0L, "superadmin")))
        assertThatThrownBy { service.create(manager(null, 0L, "superadmin")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .hasMessageContaining("superadmin")
        verify(managers, never()).save(anyOrNull<CcfaManager>())
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    /**
     * 교차 경로: SuperManagerService 로 이미 존재하는 USER_ID(테넌트 운영자의 것)를 만들려 하면
     * 거부된다. USER_ID 중복 검사는 findByUserId() 로 테넌트와 무관하게 전역이기 때문이다.
     */
    @Test fun 테넌트_운영자의_USER_ID_로는_슈퍼관리자_계정을_만들_수_없다() {
        loginSuperSelecting(9L)
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager(3L, 5L, "kbadmin")))
        assertThatThrownBy { service.create(manager(null, 0L, "kbadmin")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .hasMessageContaining("kbadmin")
        verify(managers, never()).save(anyOrNull<CcfaManager>())
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    /**
     * 잠금이 존재 검사보다 먼저 일어나는지를 InOrder 로 본다.
     * ManagerServiceTest.createLocksTableBeforeDuplicateCheck 와 같은 성질을 이 화면 쪽에도 고정한다.
     */
    @Test fun createLocksTableBeforeDuplicateCheck() {
        loginSuperSelecting(9L)
        whenever(managers.findByUserId("newsuper")).thenReturn(Optional.empty())
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { inv -> val m = inv.getArgument<CcfaManager>(0); m.idx = 11L; m }

        service.create(manager(null, 0L, "newsuper"))

        val order = Mockito.inOrder(em, lockQuery, managers)
        order.verify(em).createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE")
        order.verify(lockQuery).executeUpdate()
        order.verify(managers).findByUserId("newsuper")
    }

    @Test fun defaultsFilledOnCreate() {
        loginSuperSelecting(9L)
        whenever(managers.findByUserId("newsuper")).thenReturn(Optional.empty())
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { inv -> val m = inv.getArgument<CcfaManager>(0); m.idx = 10L; m }

        val saved = service.create(manager(null, 0L, "newsuper"))

        assertThat(saved.status).isEqualTo("활성")
        assertThat(saved.login).isEqualTo("OFF-LINE")
        assertThat(saved.createdtime).isNotNull()
        assertThat(saved.updatedtime).isNotNull()
        verify(audit).logGlobal(AuditType.CREATE, "CCFA_MANAGER CREATE 10")
    }

    @Test fun createTouchesPasswordAge() {
        loginSuperSelecting(9L)
        whenever(managers.findByUserId("newsuper")).thenReturn(Optional.empty())
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { inv -> val m = inv.getArgument<CcfaManager>(0); m.idx = 12L; m }

        service.create(manager(null, 0L, "newsuper"))

        verify(em).flush()
        verify(expiry).touch("newsuper")
    }

    @Test fun updateTouchesOnlyWhenPasswordChanged() {
        loginSuperSelecting(9L)
        val m = manager(5L, 0L, "superadmin")
        whenever(managers.findById(5L)).thenReturn(Optional.of(m))
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { it.getArgument<CcfaManager>(0) }

        service.update(5L) { e -> e.userNm = "새이름" }
        verify(expiry, never()).touch(anyOrNull())

        service.update(5L) { e -> e.userPw = "new-hash" }
        verify(expiry).touch("superadmin")
    }

    @Test fun unlockDelegatesAndAuditsStatus() {
        loginSuperSelecting(9L)
        val m = manager(2L, 0L, "superadmin")
        whenever(managers.findById(2L)).thenReturn(Optional.of(m))

        service.unlock(2L)

        verify(loginAttempts).unlock("superadmin")
        verify(audit).logGlobal(AuditType.STATUS, "CCFA_MANAGER UNLOCK superadmin")
    }

    /** 승인대기 행의 상태는 이 화면에서도 바꿀 수 없다. ManagerService.update() 와 같은 규칙이다. */
    @Test fun updateCannotActivatePendingRow() {
        loginSuperSelecting(9L)
        val m = manager(2L, 0L, "applicant")
        m.status = SignupPolicy.STATUS_PENDING
        whenever(managers.findById(2L)).thenReturn(Optional.of(m))

        assertThatThrownBy { service.update(2L) { e -> e.userNm = "이름만 바꿈"; e.status = "활성" } }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("가입 승인")

        assertThat(m.status).isEqualTo(SignupPolicy.STATUS_PENDING)
        verify(managers, never()).save(anyOrNull<CcfaManager>())
    }

    /**
     * 조작된 POST 로 companyIdx 를 다른 테넌트로 실어 보내도 저장되는 값은 항상 0 이어야 한다.
     * companyIdxAttribute() 가 null 이라 CrudService.update() 의 쓰기 측 정규화가 건너뛰어지고,
     * checkTenant() 는 mutator 실행 전(행을 읽을 때)만 검사하므로 이 clamp 가 없으면
     * ManagerForm.applyTo() 가 그대로 옮긴 companyIdx=7 이 저장되어, 이 화면이 관리하던 행이
     * 다른 테넌트로 옮겨지는 편도 함정(되돌릴 UI가 없다)이 생긴다.
     */
    @Test fun 수정_시_companyIdx_를_조작해도_0_으로_저장된다() {
        loginSuperSelecting(9L)
        val m = manager(2L, 0L, "superadmin")
        whenever(managers.findById(2L)).thenReturn(Optional.of(m))
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { it.getArgument<CcfaManager>(0) }

        val saved = service.update(2L) { e -> e.companyIdx = 7L }

        assertThat(saved.companyIdx).isEqualTo(0L)
        val captor = argumentCaptor<CcfaManager>()
        verify(managers).save(captor.capture())
        assertThat(captor.lastValue.companyIdx).isEqualTo(0L)
    }

    /**
     * toSpecification() 이 실제로 companyIdx = 0 조건을 거는지, JPA Criteria API 를 최소한만
     * 흉내 낸 스텁으로 확인한다(cb.equal() 호출에 쓰인 값을 그대로 기록한다).
     */
    @Suppress("UNCHECKED_CAST")
    private fun companyIdxEqualsIn(spec: Specification<CcfaManager>): Long {
        val cb = mock(CriteriaBuilder::class.java)
        val root = mock(Root::class.java) as Root<CcfaManager>
        val query = mock(CriteriaQuery::class.java) as CriteriaQuery<*>
        val path = mock(Path::class.java) as Path<Any>
        whenever(root.get<Any>("companyIdx")).thenReturn(path)
        val valueCaptor = argumentCaptor<Any>()
        whenever(cb.equal(anyOrNull<Expression<*>>(), valueCaptor.capture())).thenReturn(mock(Predicate::class.java))
        whenever(cb.and(anyVararg<Predicate>()))
            .thenReturn(mock(Predicate::class.java))
        spec.toPredicate(root, query, cb)
        return valueCaptor.allValues.first { it is Long } as Long
    }
}
