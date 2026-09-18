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
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import com.crosscert.fidoadmin.system.service.SystemInfoService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SystemInfoController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class SystemInfoControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean SystemInfoService service;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaSystemInfo info(String key, String value) { CcfaSystemInfo i = new CcfaSystemInfo(); i.setPropKey(key); i.setPropValue(value); return i; }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/info").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void listRendersRows() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("propKey"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(info("VERSION", "1.0.0"))));
        mvc.perform(get("/system/info").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/list"))
            .andExpect(content().string(containsString("VERSION")))
            .andExpect(content().string(containsString("/system/info/VERSION")));
    }

    @Test void detailRenders() throws Exception {
        when(service.get("VERSION")).thenReturn(info("VERSION", "1.0.0"));
        mvc.perform(get("/system/info/VERSION").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/detail"))
            .andExpect(content().string(containsString("1.0.0")));
    }

    @Test void blankKeyShowsFormAgain() throws Exception {
        mvc.perform(post("/system/info").with(user(superUser)).with(csrf()).param("propKey", "").param("propValue", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(info("NEW_KEY", "x"));
        when(service.idOf(any())).thenReturn("NEW_KEY");
        mvc.perform(post("/system/info").with(user(superUser)).with(csrf()).param("propKey", "NEW_KEY").param("propValue", "x"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/info/NEW_KEY"));
    }

    /** propKey 는 URL 경로 세그먼트로 그대로 쓰이므로 안전한 문자만 허용한다. */
    @Test void invalidKeyCharacterShowsFormAgain() throws Exception {
        mvc.perform(post("/system/info").with(user(superUser)).with(csrf()).param("propKey", "a{x}").param("propValue", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/form"))
            .andExpect(content().string(containsString("영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다")));
        verify(service, never()).create(any());
    }

    /** "new" 는 등록 폼 경로와 겹쳐 예약어로 거부한다. */
    @Test void reservedKeyNewIsRejected() throws Exception {
        mvc.perform(post("/system/info").with(user(superUser)).with(csrf()).param("propKey", "new").param("propValue", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/info/form"))
            .andExpect(content().string(containsString("로 쓸 수 없는 값입니다: new")));
        verify(service, never()).create(any());
    }
}
