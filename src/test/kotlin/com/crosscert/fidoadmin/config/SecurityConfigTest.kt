package com.crosscert.fidoadmin.config

import com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
import com.crosscert.fidoadmin.auth.LoginController
import com.crosscert.fidoadmin.auth.LoginFailureHandler
import com.crosscert.fidoadmin.auth.LoginSuccessHandler
import com.crosscert.fidoadmin.auth.ManagerUserDetailsService
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.security.web.session.HttpSessionEventPublisher
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * 동시 세션 제어(maximumSessions)는 SessionRegistry 가 세션 소멸을 들어야 정리된다.
 * HttpSessionEventPublisher 가 없으면 만료·무효화된 세션 정보가 레지스트리에 영영 쌓인다.
 */
@WebMvcTest(controllers = [LoginController::class])
@Import(SecurityConfig::class, MenuRegistry::class, TenantContext::class, SelectedTenant::class)
class SecurityConfigTest {

    @Autowired lateinit var context: ApplicationContext

    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService
    @MockitoBean lateinit var companies: CompanyLookup

    @Test fun publishesSessionEventsToTheSessionRegistry() {
        assertThat(context.getBeansOfType(HttpSessionEventPublisher::class.java)).hasSize(1)
    }

    /**
     * SUPER 전용 경로 목록은 필터체인 안의 매처와 같은 상수에서 나와야 한다. 그래야 ArchitectureRulesTest 가
     * MenuRegistry.superOnly 와 교차 검증할 수 있다. /criteria 는 의도된 예외(COMPANY 도 쓴다).
     */
    @Test fun superPathsAreExposedForCrossChecking() {
        assertThat(SecurityConfig.isSuperPath("/companies")).isTrue()
        assertThat(SecurityConfig.isSuperPath("/companies/12/edit")).isTrue()
        assertThat(SecurityConfig.isSuperPath("/signups")).isTrue()
        assertThat(SecurityConfig.isSuperPath("/managers/super")).isTrue()
        assertThat(SecurityConfig.isSuperPath("/criteria")).isFalse()
        assertThat(SecurityConfig.isSuperPath("/users/1")).isFalse()
        assertThat(SecurityConfig.isSuperPath("/")).isFalse()
    }
}
