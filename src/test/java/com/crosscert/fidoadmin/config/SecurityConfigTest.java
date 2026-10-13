package com.crosscert.fidoadmin.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.auth.LoginController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 동시 세션 제어(maximumSessions)는 SessionRegistry 가 세션 소멸을 들어야 정리된다.
 * HttpSessionEventPublisher 가 없으면 만료·무효화된 세션 정보가 레지스트리에 영영 쌓인다.
 */
@WebMvcTest(controllers = LoginController.class)
@Import({SecurityConfig.class, com.crosscert.fidoadmin.common.MenuRegistry.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class SecurityConfigTest {

    @Autowired ApplicationContext context;

    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;

    @Test void publishesSessionEventsToTheSessionRegistry() {
        assertThat(context.getBeansOfType(HttpSessionEventPublisher.class)).hasSize(1);
    }

    /**
     * SUPER 전용 경로 목록은 필터체인 안의 매처와 같은 상수에서 나와야 한다. 그래야 ArchitectureRulesTest 가
     * MenuRegistry.superOnly 와 교차 검증할 수 있다. /criteria 는 의도된 예외(COMPANY 도 쓴다).
     */
    @Test void superPathsAreExposedForCrossChecking() {
        assertThat(SecurityConfig.isSuperPath("/companies")).isTrue();
        assertThat(SecurityConfig.isSuperPath("/companies/12/edit")).isTrue();
        assertThat(SecurityConfig.isSuperPath("/signups")).isTrue();
        assertThat(SecurityConfig.isSuperPath("/managers/super")).isTrue();
        assertThat(SecurityConfig.isSuperPath("/criteria")).isFalse();
        assertThat(SecurityConfig.isSuperPath("/users/1")).isFalse();
        assertThat(SecurityConfig.isSuperPath("/")).isFalse();
    }
}
