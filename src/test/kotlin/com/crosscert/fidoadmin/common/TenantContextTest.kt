package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class TenantContextTest {

    var selected = SelectedTenant()
    var tenant = TenantContext(selected)

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun login(companyIdx: Long) {
        val u = ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(u, null, u.authorities)
    }

    @Test fun COMPANY_는_자기_소속이_유효_테넌트다() {
        login(5L)
        assertThat(tenant.companyIdx()).isEqualTo(5L)
        assertThat(tenant.hasTenant()).isTrue()
    }

    /** COMPANY 는 세션 선택값을 읽지 않는다. 다른 값이 들어 있어도 자기 소속이다. */
    @Test fun COMPANY_는_세션_선택을_무시한다() {
        login(5L)
        selected.select(9L)
        assertThat(tenant.companyIdx()).isEqualTo(5L)
    }

    @Test fun SUPER_는_선택한_고객사가_유효_테넌트다() {
        login(0L)
        selected.select(9L)
        assertThat(tenant.companyIdx()).isEqualTo(9L)
        assertThat(tenant.hasTenant()).isTrue()
    }

    /** 미선택을 null 로 돌려주면 필터가 조용히 사라진다. 예외여야 한다. */
    @Test fun SUPER_가_미선택이면_예외다() {
        login(0L)
        assertThat(tenant.hasTenant()).isFalse()
        assertThatThrownBy { tenant.companyIdx() }
            .isInstanceOf(NoTenantSelectedException::class.java)
    }

    @Test fun 로그인이_없으면_require_가_실패한다() {
        assertThatThrownBy { tenant.require() }.isInstanceOf(IllegalStateException::class.java)
        assertThat(tenant.current()).isEmpty()
        assertThat(tenant.hasTenant()).isFalse()
    }
}
