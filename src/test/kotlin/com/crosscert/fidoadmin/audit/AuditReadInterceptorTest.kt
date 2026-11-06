package com.crosscert.fidoadmin.audit

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class AuditReadInterceptorTest {

    private companion object {
        /** Java 판은 handler 자리에 null 을 넘겼다. Kotlin 오버라이드는 Spring 시그니처대로 non-null 이라 빈 객체를 넘긴다(인터셉터는 handler 를 보지 않는다). */
        val HANDLER = Any()
    }

    val audit: AuditLogger = mock(AuditLogger::class.java)
    val tenant = TenantContext(SelectedTenant())
    lateinit var interceptor: AuditReadInterceptor

    val request = MockHttpServletRequest("GET", "/users/5")
    val response = MockHttpServletResponse()

    @Suppress("UNCHECKED_CAST")
    @BeforeEach fun setUp() {
        val auditProvider = mock(ObjectProvider::class.java) as ObjectProvider<AuditLogger>
        whenever(auditProvider.getIfAvailable()).thenReturn(audit)
        val tenantProvider = mock(ObjectProvider::class.java) as ObjectProvider<TenantContext>
        whenever(tenantProvider.getIfAvailable()).thenReturn(tenant)
        interceptor = AuditReadInterceptor(auditProvider, tenantProvider, MenuRegistry())

        val u = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(u, null, u.authorities)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    @AfterEach fun tearDown() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes()
    }

    /** 대상을 적어 두지 않은 화면은 진입 사실만 남긴다. 메뉴 이름은 경로에서 푼다. */
    @Test fun listViewLeavesOneMenuViewRow() {
        interceptor.afterCompletion(request, response, HANDLER, null)

        val msg = ArgumentCaptor.forClass(String::class.java)
        verify(audit).log(eq(AuditType.MENU_VIEW), msg.capture())
        assertThat(msg.value).isEqualTo("FIDO 등록자 관리 화면 조회")
    }

    /** 컨트롤러가 AuditView 로 대상을 적어 두면 그 건수만큼 DATA_VIEW 가 남는다. */
    @Test fun detailViewLeavesOneDataViewRowPerTarget() {
        AuditView.add("USERINFO", "5")
        AuditView.add("USERINFO", "6")

        interceptor.afterCompletion(request, response, HANDLER, null)

        val msg = ArgumentCaptor.forClass(String::class.java)
        verify(audit, times(2)).log(eq(AuditType.DATA_VIEW), msg.capture())
        assertThat(msg.allValues)
            .containsExactly("FIDO 등록자 관리 상세 조회 (ID: 5)", "FIDO 등록자 관리 상세 조회 (ID: 6)")
        verify(audit, never()).log(eq(AuditType.MENU_VIEW), anyString())
    }

    /** 변경 요청은 CREATE/UPDATE/DELETE 가 따로 남긴다. 여기서 또 남기면 한 동작이 두 줄이 된다. */
    @Test fun postIsNotRecorded() {
        request.method = "POST"

        interceptor.afterCompletion(request, response, HANDLER, null)

        verify(audit, never()).log(any<AuditType>(), anyString())
    }

    /** 리다이렉트(테넌트 미선택 등)는 화면을 보여 준 것이 아니다. */
    @Test fun redirectIsNotRecorded() {
        response.status = 302

        interceptor.afterCompletion(request, response, HANDLER, null)

        verify(audit, never()).log(any<AuditType>(), anyString())
    }

    /** 예외로 끝난 요청은 조회가 아니다. */
    @Test fun failedRequestIsNotRecorded() {
        interceptor.afterCompletion(request, response, HANDLER, RuntimeException("boom"))

        verify(audit, never()).log(any<AuditType>(), anyString())
    }

    /** 로그인 전(시큐리티가 튕기기 전 정적 자원 등)은 남길 주체가 없다. */
    @Test fun anonymousIsNotRecorded() {
        SecurityContextHolder.clearContext()

        interceptor.afterCompletion(request, response, HANDLER, null)

        verify(audit, never()).log(any<AuditType>(), anyString())
    }

    /**
     * AuditLogger 가 없는 컨텍스트(@WebMvcTest 조각)에서는 조용히 건너뛴다.
     * 생성자 주입이었다면 컨텍스트 자체가 뜨지 않아 웹 테스트 전부가 깨졌다.
     */
    @Suppress("UNCHECKED_CAST")
    @Test fun missingLoggerIsSkippedNotFatal() {
        val none = mock(ObjectProvider::class.java) as ObjectProvider<AuditLogger>
        whenever(none.getIfAvailable()).thenReturn(null)
        val tenantProvider = mock(ObjectProvider::class.java) as ObjectProvider<TenantContext>
        whenever(tenantProvider.getIfAvailable()).thenReturn(tenant)
        val bare = AuditReadInterceptor(none, tenantProvider, MenuRegistry())

        bare.afterCompletion(request, response, HANDLER, null)

        verify(audit, never()).log(any<AuditType>(), anyString())
    }

    /** 메뉴에 없는 경로는 경로 자체를 이름으로 남긴다 — 비어 있는 것보다 낫다. */
    @Test fun unknownPathFallsBackToPathAsMenuName() {
        request.requestURI = "/nowhere/1"

        interceptor.afterCompletion(request, response, HANDLER, null)

        verify(audit).log(eq(AuditType.MENU_VIEW), eq("/nowhere/1 화면 조회"))
    }
}
