package com.crosscert.fidoadmin.system.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import com.crosscert.fidoadmin.system.service.SystemPropService;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 시스템 설정은 식별자가 URL 에 그대로 들어간다({@code /system/props/{key}@{companyIdx}}).
 * checkTenant() 가 이미 Task 3 에서 다른 테넌트의 값을 막지만, 이 화면에서는 식별자가 URL
 * 세그먼트로만 보여서 그 보호가 가장 눈에 잘 띄지 않는다 — 그래서 실제 서비스(목이 아니다)를
 * 태워 리포지터리에 존재하는 값이라도 다른 테넌트의 것이면 404 인지 직접 확인한다.
 * 존재 자체를 숨겨야 하므로 403 이 아니라 404 다.
 */
@WebMvcTest(controllers = SystemPropController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
         SystemPropIdConverter.class, SystemPropService.class,
         TenantContext.class, SelectedTenant.class})
class SystemPropUrlTenantTest {

    @Autowired MockMvc mvc;
    @Autowired SelectedTenant selected;
    @MockitoBean CcfaSystemPropRepository repository;
    @MockitoBean AuditLogger audit;
    @MockitoBean CompanyLookup companies;
    @MockitoBean EntityManager em;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);

    MockHttpSession session;

    /** TenantSelectionInterceptor 가 미선택 SUPER 를 돌려보내므로, 세션에 미리 9 를 선택해 둔다. */
    @BeforeEach void selectTenant() {
        session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            selected.select(9L);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private CcfaSystemProp prop(String key, long companyIdx, String value) {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId(key, companyIdx));
        p.setPropValue(value);
        p.setShareType("NO");
        return p;
    }

    /**
     * 식별자가 URL 에 들어간다(/system/props/{key}@{companyIdx}). 다른 테넌트의 값을
     * 넣어도 열리지 않아야 한다. 존재 자체를 숨기므로 403 이 아니라 404 다.
     */
    @Test void 시스템_설정은_URL_의_다른_테넌트를_거부한다() throws Exception {
        when(repository.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 3L)))
            .thenReturn(Optional.of(prop("PW_FAIL_LIMIT", 3L, "5")));
        mvc.perform(get("/system/props/PW_FAIL_LIMIT@3").session(session).with(user(superUser)))
            .andExpect(status().isNotFound());
    }

    @Test void 시스템_설정은_선택한_테넌트의_값은_연다() throws Exception {
        when(repository.findById(new CcfaSystemPropId("PW_FAIL_LIMIT", 9L)))
            .thenReturn(Optional.of(prop("PW_FAIL_LIMIT", 9L, "5")));
        when(companies.name(9L)).thenReturn("고객사9");
        mvc.perform(get("/system/props/PW_FAIL_LIMIT@9").session(session).with(user(superUser)))
            .andExpect(status().isOk());
    }
}
