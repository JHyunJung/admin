package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.UsernameNotFoundException

class ManagerUserDetailsServiceTest {

    val managers: CcfaManagerRepository = mock(CcfaManagerRepository::class.java)
    val policies: CcfaManagerPwPolicyRepository = mock(CcfaManagerPwPolicyRepository::class.java)
    val companies: CcfaCompanyRepository = mock(CcfaCompanyRepository::class.java)
    val props: CcfaSystemPropRepository = mock(CcfaSystemPropRepository::class.java)
    // 잠금 판정은 실제 LoginAttemptService 로 한다(mock 이면 항상 "안 잠김"이 되어 기존 잠금 테스트가 무의미해진다).
    val attempts = LoginAttemptService(managers, policies, props, 5, 30)
    val service = ManagerUserDetailsService(managers, policies, companies, attempts)

    private fun manager(status: String, companyIdx: Long?): CcfaManager {
        val m = CcfaManager()
        m.idx = 1L; m.userId = "kbadmin"; m.userPw = "hash"; m.userNm = "KB운영자"
        m.status = status; m.companyIdx = companyIdx
        return m
    }

    /**
     * COMPANY_IDX 가 null 인 계정은 로그인이 거부되어야 한다.
     * null 을 0 으로 치환하면 isSuper() 가 참이 되어 슈퍼 관리자로 승격된다.
     */
    @Test fun nullCompanyIdxIsRejectedInsteadOfBecomingSuper() {
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", null)))
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty())

        assertThatThrownBy { service.loadUserByUsername("kbadmin") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("COMPANY_IDX")
    }

    /** COMPANY_IDX 가 null 이면 회사명 조회로 DB 를 찌르지 않는다. */
    @Test fun nullCompanyIdxDoesNotQueryCompany() {
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", null)))
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty())

        assertThatThrownBy { service.loadUserByUsername("kbadmin") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(companies, never()).findById(anyOrNull())
    }

    @Test fun loadsActiveManagerWithCompanyRole() {
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", 1L)))
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty())
        val c = CcfaCompany(); c.idx = 1L; c.companyName = "KB국민은행"
        whenever(companies.findById(1L)).thenReturn(Optional.of(c))

        val u = service.loadUserByUsername("kbadmin") as ManagerUserDetails

        assertThat(u.isEnabled()).isTrue()
        assertThat(u.isAccountNonLocked()).isTrue()
        assertThat(u.companyName).isEqualTo("KB국민은행")
        assertThat(u.authorities).extracting("authority").containsExactly("ROLE_COMPANY")
    }

    @Test fun inactiveStatusIsDisabled() {
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("비활성", 0L)))
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty())
        whenever(companies.findById(0L)).thenReturn(Optional.empty())

        val u = service.loadUserByUsername("kbadmin") as ManagerUserDetails
        assertThat(u.isEnabled()).isFalse()
        assertThat(u.isSuper).isTrue()
        assertThat(u.companyName).isEqualTo("전역")
    }

    @Test fun lockedPolicyLocksAccount() {
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", 1L)))
        val p = CcfaManagerPwPolicy(); p.accountLock = "Y"
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p))
        whenever(companies.findById(1L)).thenReturn(Optional.empty())

        val u = service.loadUserByUsername("kbadmin") as ManagerUserDetails
        assertThat(u.isAccountNonLocked()).isFalse()
    }

    @Test fun unknownUserThrows() {
        whenever(managers.findByUserId("x")).thenReturn(Optional.empty())
        assertThatThrownBy { service.loadUserByUsername("x") }.isInstanceOf(UsernameNotFoundException::class.java)
    }

    @Test fun nullNameFallsBackToUserId() {
        val m = manager("활성", 1L); m.userNm = null
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(m))
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty())
        whenever(companies.findById(1L)).thenReturn(Optional.empty())
        assertThat(service.loadUserByUsername("kbadmin")).extracting("userNm").isEqualTo("kbadmin")
    }

    /** 잠근 지 30분이 지난 계정은 ACCOUNT_LOCK='Y' 여도 로그인할 수 있다. */
    @Test fun expiredLockIsTreatedAsUnlocked() {
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", 1L)))
        val p = CcfaManagerPwPolicy()
        p.accountLock = "Y"; p.updatedtime = LocalDateTime.now().minusMinutes(31)
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p))

        assertThat(service.loadUserByUsername("kbadmin").isAccountNonLocked()).isTrue()
    }

    @Test fun freshLockIsStillLocked() {
        whenever(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", 1L)))
        val p = CcfaManagerPwPolicy()
        p.accountLock = "Y"; p.updatedtime = LocalDateTime.now().minusMinutes(5)
        whenever(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p))

        assertThat(service.loadUserByUsername("kbadmin").isAccountNonLocked()).isFalse()
    }
}
