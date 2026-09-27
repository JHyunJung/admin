package com.crosscert.fidoadmin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditReadInterceptorTest {

    AuditLogger audit = mock(AuditLogger.class);
    TenantContext tenant = new TenantContext(new SelectedTenant());
    AuditReadInterceptor interceptor;

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/5");
    MockHttpServletResponse response = new MockHttpServletResponse();

    @SuppressWarnings("unchecked")
    @BeforeEach void setUp() {
        ObjectProvider<AuditLogger> auditProvider = mock(ObjectProvider.class);
        when(auditProvider.getIfAvailable()).thenReturn(audit);
        ObjectProvider<TenantContext> tenantProvider = mock(ObjectProvider.class);
        when(tenantProvider.getIfAvailable()).thenReturn(tenant);
        interceptor = new AuditReadInterceptor(auditProvider, tenantProvider, new MenuRegistry());

        var u = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    /** 대상을 적어 두지 않은 화면은 진입 사실만 남긴다. 메뉴 이름은 경로에서 푼다. */
    @Test void listViewLeavesOneMenuViewRow() {
        interceptor.afterCompletion(request, response, null, null);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq(AuditType.MENU_VIEW), msg.capture());
        assertThat(msg.getValue()).isEqualTo("FIDO 등록자 관리 화면 조회");
    }

    /** 컨트롤러가 AuditView 로 대상을 적어 두면 그 건수만큼 DATA_VIEW 가 남는다. */
    @Test void detailViewLeavesOneDataViewRowPerTarget() {
        AuditView.add("USERINFO", "5");
        AuditView.add("USERINFO", "6");

        interceptor.afterCompletion(request, response, null, null);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit, times(2)).log(eq(AuditType.DATA_VIEW), msg.capture());
        assertThat(msg.getAllValues())
            .containsExactly("FIDO 등록자 관리 상세 조회 (ID: 5)", "FIDO 등록자 관리 상세 조회 (ID: 6)");
        verify(audit, never()).log(eq(AuditType.MENU_VIEW), anyString());
    }

    /** 변경 요청은 CREATE/UPDATE/DELETE 가 따로 남긴다. 여기서 또 남기면 한 동작이 두 줄이 된다. */
    @Test void postIsNotRecorded() {
        request.setMethod("POST");

        interceptor.afterCompletion(request, response, null, null);

        verify(audit, never()).log(any(AuditType.class), anyString());
    }

    /** 리다이렉트(테넌트 미선택 등)는 화면을 보여 준 것이 아니다. */
    @Test void redirectIsNotRecorded() {
        response.setStatus(302);

        interceptor.afterCompletion(request, response, null, null);

        verify(audit, never()).log(any(AuditType.class), anyString());
    }

    /** 예외로 끝난 요청은 조회가 아니다. */
    @Test void failedRequestIsNotRecorded() {
        interceptor.afterCompletion(request, response, null, new RuntimeException("boom"));

        verify(audit, never()).log(any(AuditType.class), anyString());
    }

    /** 로그인 전(시큐리티가 튕기기 전 정적 자원 등)은 남길 주체가 없다. */
    @Test void anonymousIsNotRecorded() {
        SecurityContextHolder.clearContext();

        interceptor.afterCompletion(request, response, null, null);

        verify(audit, never()).log(any(AuditType.class), anyString());
    }

    /**
     * AuditLogger 가 없는 컨텍스트(@WebMvcTest 조각)에서는 조용히 건너뛴다.
     * 생성자 주입이었다면 컨텍스트 자체가 뜨지 않아 웹 테스트 전부가 깨졌다.
     */
    @SuppressWarnings("unchecked")
    @Test void missingLoggerIsSkippedNotFatal() {
        ObjectProvider<AuditLogger> none = mock(ObjectProvider.class);
        when(none.getIfAvailable()).thenReturn(null);
        ObjectProvider<TenantContext> tenantProvider = mock(ObjectProvider.class);
        when(tenantProvider.getIfAvailable()).thenReturn(tenant);
        AuditReadInterceptor bare = new AuditReadInterceptor(none, tenantProvider, new MenuRegistry());

        bare.afterCompletion(request, response, null, null);

        verify(audit, never()).log(any(AuditType.class), anyString());
    }

    /** 메뉴에 없는 경로는 경로 자체를 이름으로 남긴다 — 비어 있는 것보다 낫다. */
    @Test void unknownPathFallsBackToPathAsMenuName() {
        request.setRequestURI("/nowhere/1");

        interceptor.afterCompletion(request, response, null, null);

        verify(audit).log(eq(AuditType.MENU_VIEW), eq("/nowhere/1 화면 조회"));
    }
}
