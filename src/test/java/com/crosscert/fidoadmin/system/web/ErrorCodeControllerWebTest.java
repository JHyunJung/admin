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
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable;
import com.crosscert.fidoadmin.system.service.ErrorCodeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ErrorCodeController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class ErrorCodeControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean ErrorCodeService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaErrorTable code(String c, String msg) { CcfaErrorTable e = new CcfaErrorTable(); e.setErrorCode(c); e.setErrorMessage(msg); e.setErrorType("ERROR"); return e; }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/error-codes").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void listRendersRows() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("errorCode"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(code("1498", "INVALID_SIGNATURE"))));
        mvc.perform(get("/system/error-codes").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/list"))
            .andExpect(content().string(containsString("INVALID_SIGNATURE")))
            .andExpect(content().string(containsString("/system/error-codes/1498")));
    }

    @Test void detailRenders() throws Exception {
        when(service.get("1498")).thenReturn(code("1498", "INVALID_SIGNATURE"));
        mvc.perform(get("/system/error-codes/1498").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/detail"))
            .andExpect(content().string(containsString("INVALID_SIGNATURE")));
    }

    @Test void codeOverByteLimitShowsFormAgain() throws Exception {
        mvc.perform(post("/system/error-codes").with(user(superUser)).with(csrf())
                .param("errorCode", "1".repeat(21)).param("errorMessage", "x"))   // VARCHAR2(20)
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/form"))
            .andExpect(content().string(containsString("바이트를 넘을 수 없습니다")));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(code("1499", "x"));
        when(service.idOf(any())).thenReturn("1499");
        mvc.perform(post("/system/error-codes").with(user(superUser)).with(csrf()).param("errorCode", "1499").param("errorMessage", "x"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/error-codes/1499"));
    }

    /** errorCode 는 URL 경로 세그먼트로 그대로 쓰이므로 안전한 문자만 허용한다. */
    @Test void invalidCodeCharacterShowsFormAgain() throws Exception {
        mvc.perform(post("/system/error-codes").with(user(superUser)).with(csrf()).param("errorCode", "a{x}").param("errorMessage", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/form"))
            .andExpect(content().string(containsString("영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다")));
        verify(service, never()).create(any());
    }

    /** "new" 는 등록 폼 경로와 겹쳐 예약어로 거부한다. */
    @Test void reservedCodeNewIsRejected() throws Exception {
        mvc.perform(post("/system/error-codes").with(user(superUser)).with(csrf()).param("errorCode", "new").param("errorMessage", "x"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/error-codes/form"))
            .andExpect(content().string(containsString("로 쓸 수 없는 값입니다: new")));
        verify(service, never()).create(any());
    }
}
