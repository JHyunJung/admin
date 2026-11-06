package com.crosscert.fidoadmin.manager.service

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.LoginAttemptService
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.signup.SignupPolicy
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import jakarta.persistence.Query
import java.util.Optional
import java.util.function.Function
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class ManagerServiceTest {

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
    val service = ManagerService(managers, audit, policies, loginAttempts, em, tenant, userIdGuard, expiry)

    @BeforeEach fun loginSuper() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(u, null, u.authorities)
        whenever(em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE")).thenReturn(lockQuery)
        selected.select(1L) // manager() 가 만드는 행의 소유 COMPANY_IDX 와 맞춘다
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun manager(idx: Long?, userId: String): CcfaManager {
        val m = CcfaManager(); m.idx = idx; m.userId = userId; m.userPw = "hash"; m.companyIdx = 1L
        return m
    }

    /** USER_ID 에 유니크 제약이 없어 코드에서 중복을 막는다. 로그인이 USER_ID 로 조회하기 때문. */
    @Test fun duplicateUserIdIsRejectedBeforeSave() {
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager(2L, "kbadmin")))
        assertThatThrownBy { service.create(manager(null, "kbadmin")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .hasMessageContaining("kbadmin")
        verify(managers, never()).save(anyOrNull<CcfaManager>())
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    /**
     * 교차 경로: ManagerService(테넌트 운영자 화면)로 이미 존재하는 USER_ID(슈퍼관리자 계정의 것,
     * COMPANY_IDX=0)를 만들려 하면 거부된다. USER_ID 중복 검사는 findByUserId() 로 테넌트와
     * 무관하게 전역이라, 어느 화면에서 왔는지와 무관하게 같은 USER_ID 는 하나만 존재해야 한다.
     * SuperManagerServiceTest.테넌트_운영자의_USER_ID_로는_슈퍼관리자_계정을_만들_수_없다() 와
     * 짝을 이루는 반대 방향 테스트다.
     */
    @Test fun 슈퍼관리자_계정의_USER_ID_로는_테넌트_운영자를_만들_수_없다() {
        val superAdmin = CcfaManager()
        superAdmin.idx = 3L; superAdmin.userId = "superadmin"; superAdmin.userPw = "hash"; superAdmin.companyIdx = 0L
        whenever(managers.findByUserId("superadmin")).thenReturn(Optional.of(superAdmin))
        assertThatThrownBy { service.create(manager(null, "superadmin")) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .hasMessageContaining("superadmin")
        verify(managers, never()).save(anyOrNull<CcfaManager>())
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    /**
     * USER_ID 에 DB 유니크 제약이 없어(스키마 변경 불가) 동시 등록 요청이 둘 다
     * findByUserId() 에서 빈 결과를 볼 수 있다. 존재 검사 전에 테이블을 배타 잠금해
     * 이후 생성 요청을 직렬화해야 한다.
     */
    @Test fun createLocksTableBeforeDuplicateCheck() {
        whenever(managers.findByUserId("newop")).thenReturn(Optional.empty())
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { inv -> val m = inv.getArgument<CcfaManager>(0); m.idx = 11L; m }

        service.create(manager(null, "newop"))

        val order = Mockito.inOrder(em, lockQuery, managers)
        order.verify(em).createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE")
        order.verify(lockQuery).executeUpdate()
        order.verify(managers).findByUserId("newop")
    }

    @Test fun defaultsFilledOnCreate() {
        whenever(managers.findByUserId("newop")).thenReturn(Optional.empty())
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { inv -> val m = inv.getArgument<CcfaManager>(0); m.idx = 10L; m }

        val saved = service.create(manager(null, "newop"))

        assertThat(saved.status).isEqualTo("활성")
        assertThat(saved.login).isEqualTo("OFF-LINE")
        assertThat(saved.alramType).isEqualTo("none")
        assertThat(saved.alramLevel).isEqualTo("0")
        assertThat(saved.createdtime).isNotNull()
        assertThat(saved.updatedtime).isNotNull()
        verify(audit).log(AuditType.CREATE, "CCFA_MANAGER CREATE 10")
    }

    @Test fun unlockDelegatesAndAuditsStatus() {
        val m = manager(2L, "kbadmin")
        whenever(managers.findById(2L)).thenReturn(Optional.of(m))

        service.unlock(2L)

        verify(loginAttempts).unlock("kbadmin")
        verify(audit).log(AuditType.STATUS, "CCFA_MANAGER UNLOCK kbadmin")
    }

    /**
     * get() 은 잠금 없이 읽는다. 동시 로그인이 LOGIN/LAST_ACCESS/BLOCK_TIME/UPDATEDTIME 을 바꾼 뒤
     * save() 가 그 값을 덮어쓰지 않도록, findById() 이후·loginAttempts.unlock() 이전에
     * PESSIMISTIC_WRITE 로 행을 재조회(refresh)해 잠가야 한다.
     */
    @Test fun unlockRefreshesUnderRowLockBeforeDelegating() {
        val m = manager(2L, "kbadmin")
        whenever(managers.findById(2L)).thenReturn(Optional.of(m))

        service.unlock(2L)

        val order = Mockito.inOrder(managers, em, loginAttempts)
        order.verify(managers).findById(2L)
        order.verify(em).refresh(m, LockModeType.PESSIMISTIC_WRITE)
        order.verify(loginAttempts).unlock("kbadmin")
    }

    @Test fun selfDeleteIsBlocked() {
        whenever(managers.findById(1L)).thenReturn(Optional.of(manager(1L, "superuser")))
        assertThatThrownBy { service.delete(1L) }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("자기 자신")
        verify(managers, never()).delete(any<CcfaManager>())
    }

    /**
     * 승인대기 행을 운영자 수정 화면에서 열면 상태 선택지에 그 값이 없어 브라우저가 `활성` 을 고른다.
     * 이름만 고쳐 저장해도 상태가 `활성` 으로 바뀌어 승인 절차(감사 로그·상태 재확인·행 잠금)를
     * 통째로 건너뛰게 된다. 템플릿을 고치는 것만으로는 조작된 POST 를 막지 못하므로
     * 서비스가 거부해야 한다.
     */
    @Test fun updateCannotActivatePendingRow() {
        val m = manager(2L, "applicant")
        m.status = SignupPolicy.STATUS_PENDING
        whenever(managers.findById(2L)).thenReturn(Optional.of(m))

        assertThatThrownBy { service.update(2L) { e -> e.userNm = "이름만 바꿈"; e.status = "활성" } }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("가입 승인")

        assertThat(m.status).isEqualTo(SignupPolicy.STATUS_PENDING)
        verify(managers, never()).save(anyOrNull<CcfaManager>())
        verify(audit, never()).log(anyOrNull<AuditType>(), anyOrNull<String>())
    }

    /** 거절 행도 같다. 되살리려면 가입 승인 화면을 거쳐야 한다. */
    @Test fun updateCannotChangeStatusOfRejectedRow() {
        val m = manager(3L, "rejected")
        m.status = SignupPolicy.STATUS_REJECTED
        whenever(managers.findById(3L)).thenReturn(Optional.of(m))

        assertThatThrownBy { service.update(3L) { e -> e.status = "비활성" } }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(m.status).isEqualTo(SignupPolicy.STATUS_REJECTED)
        verify(managers, never()).save(anyOrNull<CcfaManager>())
    }

    /**
     * 막는 것은 상태 변경뿐이다. 승인대기 행의 다른 필드(이름·연락처 등) 수정은 그대로 통과하고,
     * 상태는 원래 값으로 저장된다. 화면이 상태를 읽기 전용으로 되돌려 보내므로 정상 경로다.
     */
    @Test fun updateKeepsOtherFieldsEditableOnPendingRow() {
        val m = manager(2L, "applicant")
        m.status = SignupPolicy.STATUS_PENDING
        whenever(managers.findById(2L)).thenReturn(Optional.of(m))
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { it.getArgument<CcfaManager>(0) }

        service.update(2L) { e -> e.userNm = "바뀐 이름"; e.status = SignupPolicy.STATUS_PENDING }

        assertThat(m.userNm).isEqualTo("바뀐 이름")
        assertThat(m.status).isEqualTo(SignupPolicy.STATUS_PENDING)
        verify(audit).log(eq(AuditType.UPDATE), startsWith("CCFA_MANAGER UPDATE 2"))
    }

    /** 정상 상태(활성/비활성) 행의 상태 변경은 기존 화면 그대로 동작해야 한다. */
    @Test fun updateStillTogglesStatusOnNormalRow() {
        val m = manager(2L, "kbadmin")
        m.status = "활성"
        whenever(managers.findById(2L)).thenReturn(Optional.of(m))
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { it.getArgument<CcfaManager>(0) }

        service.update(2L) { e -> e.status = "비활성" }

        assertThat(m.status).isEqualTo("비활성")
        verify(audit).log(eq(AuditType.UPDATE), startsWith("CCFA_MANAGER UPDATE 2"))
    }

    @Test fun createTouchesPasswordAge() {
        whenever(managers.findByUserId("newbie")).thenReturn(Optional.empty())
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { inv -> val m = inv.getArgument<CcfaManager>(0); m.idx = 12L; m }

        service.create(manager(null, "newbie"))

        verify(em).flush()
        verify(expiry).touch("newbie")
    }

    @Test fun updateTouchesOnlyWhenPasswordChanged() {
        val m = manager(5L, "kbadmin")
        whenever(managers.findById(5L)).thenReturn(Optional.of(m))
        whenever(managers.save(anyOrNull<CcfaManager>())).thenAnswer { it.getArgument<CcfaManager>(0) }

        service.update(5L) { e -> e.userNm = "새이름" }
        verify(expiry, never()).touch(anyOrNull())

        service.update(5L) { e -> e.userPw = "new-hash" }
        verify(expiry).touch("kbadmin")
    }

    @Test fun lockStateReadsLatestPolicyRow() {
        val p = CcfaManagerPwPolicy(); p.userId = "kbadmin"; p.accountLock = "Y"; p.pwFailCnt = 5L
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p))
        assertThat(service.lockState("kbadmin")).get()
            .extracting(Function<CcfaManagerPwPolicy, String?> { it.accountLock }).isEqualTo("Y")
        assertThat(service.lockState("nobody")).isEmpty()
    }
}
