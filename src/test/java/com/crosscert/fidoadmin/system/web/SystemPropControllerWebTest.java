package com.crosscert.fidoadmin.system.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.service.SystemPropService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@WebMvcTest(controllers = SystemPropController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
         SystemPropIdConverter.class,
         com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class SystemPropControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired com.crosscert.fidoadmin.common.SelectedTenant selected;
    @MockitoBean SystemPropService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaSystemProp prop(String key, long company, String value) {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId(key, company)); p.setPropValue(value); p.setShareType("YES");
        return p;
    }

    /**
     * TenantSelectionInterceptor(Task 6)가 미선택 SUPER 를 /select-tenant 로 돌려보낸다.
     * 이 클래스가 보는 경로는 TENANT 영역이므로, SUPER 요청에는 미리 테넌트를 선택해 둔다.
     */
    MockHttpSession session;

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


    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/props").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void listRendersRowsWithPathLinks() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("id.propKey"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(prop("PW_FAIL_LIMIT", 0L, "5"))));
        when(companies.names()).thenReturn(Map.of(0L, "전역(시스템)"));
        mvc.perform(get("/system/props").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/list"))
            .andExpect(content().string(containsString("PW_FAIL_LIMIT")))
            .andExpect(content().string(containsString("/system/props/PW_FAIL_LIMIT@0")))
            .andExpect(content().string(containsString("전역(시스템)")));
    }

    /** 경로 변수 "PW_FAIL_LIMIT@0" 가 복합키로 변환되어 서비스에 전달된다. */
    @Test void detailConvertsCompositePathVariable() throws Exception {
        CcfaSystemPropId id = new CcfaSystemPropId("PW_FAIL_LIMIT", 0L);
        when(service.get(id)).thenReturn(prop("PW_FAIL_LIMIT", 0L, "5"));
        when(companies.name(0L)).thenReturn("전역(시스템)");
        mvc.perform(get("/system/props/PW_FAIL_LIMIT@0").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/detail"))
            .andExpect(content().string(containsString("PW_FAIL_LIMIT")))
            .andExpect(content().string(containsString("전역(시스템)")));
        verify(service).get(id);
    }

    /** 경로 값이 "키@COMPANY_IDX" 형식이 아니면 어떤 자원도 가리키지 않으므로 404. */
    @Test void malformedPathVariableIsNotFound() throws Exception {
        mvc.perform(get("/system/props/NO_AT").session(session).with(user(superUser)))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error/404"));
    }

    /**
     * PROP_KEY 는 리다이렉트 경로의 {id} 세그먼트로 그대로 쓰인다. RedirectView 가 "{...}" 를
     * URI 템플릿 변수로 해석하거나(예: "a{x}") "?"/"#" 뒤가 쿼리·프래그먼트로 잘리는 값은
     * 저장 후 리다이렉트가 깨지므로 허용 문자 집합(@Pattern)으로 미리 막는다.
     */
    @Test void keyWithSlashIsRejected() throws Exception {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "a/b").param("companyIdx", "0").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")));
        verify(service, never()).create(any());
    }

    @Test void keyWithBraceIsRejected() throws Exception {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "a{x}").param("companyIdx", "0").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")));
        verify(service, never()).create(any());
    }

    @Test void keyWithQuestionMarkIsRejected() throws Exception {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "a?b").param("companyIdx", "0").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")));
        verify(service, never()).create(any());
    }

    @Test void keyWithUpperCaseSlashIsRejected() throws Exception {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "A/B").param("companyIdx", "0").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")));
        verify(service, never()).create(any());
    }

    /** "new" 는 등록 폼 경로와 겹쳐 예약된 값으로 거부한다. */
    @Test void keyEqualToNewIsRejected() throws Exception {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "new").param("companyIdx", "0").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키로 쓸 수 없는 값입니다: new")));
        verify(service, never()).create(any());
    }

    /** 점으로만 된 값은 경로 세그먼트로서 특수한 의미를 가져 예약된 값으로 거부한다. */
    @Test void keyOfDotsOnlyIsRejected() throws Exception {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "..").param("companyIdx", "0").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키로 쓸 수 없는 값입니다: ..")));
        verify(service, never()).create(any());
    }

    @Test void createRedirectsToCompositeDetail() throws Exception {
        when(service.create(any())).thenReturn(prop("NEW_KEY", 0L, "x"));
        when(service.idOf(any())).thenReturn("NEW_KEY@0");
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "NEW_KEY").param("companyIdx", "0").param("propValue", "x").param("shareType", "NO"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/props/NEW_KEY@0"));
    }

    /** update 리다이렉트는 CrudController 가 id.toString() 으로 만든다. toString 이 경로 값이어야 한다. */
    @Test void updateRedirectsToCompositeDetail() throws Exception {
        mvc.perform(post("/system/props/PW_FAIL_LIMIT@0").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "PW_FAIL_LIMIT").param("companyIdx", "0").param("propValue", "7").param("shareType", "YES"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/props/PW_FAIL_LIMIT@0"));
    }
}
