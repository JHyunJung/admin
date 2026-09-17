package com.crosscert.fidoadmin.company.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyService;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CompanyController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class CompanyControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean CompanyService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    @Test void listRendersRows() throws Exception {
        CcfaCompany c = new CcfaCompany(); c.setIdx(1L); c.setCompanyName("KB국민은행");
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(c)));
        mvc.perform(get("/companies").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/list"))
            .andExpect(content().string(containsString("KB국민은행")));
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/companies").with(user(companyUser))).andExpect(status().isForbidden());
    }

    @Test void createWithBlankNameShowsFormAgain() throws Exception {
        mvc.perform(post("/companies").with(user(superUser)).with(csrf()).param("companyName", "").param("enableType", "Y")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/form"));
    }

    /**
     * 길이 초과처럼 companyName 이외의 필드에서 검증이 실패해도 이유가 화면에 보여야 한다.
     * 전에는 companyName·수량 필드만 메시지를 렌더링해, 저장이 조용히 실패하는 것처럼 보였다.
     */
    @Test void validationErrorOnAnyFieldIsShownToUser() throws Exception {
        mvc.perform(post("/companies").with(user(superUser)).with(csrf())
                .param("companyName", "정상이름").param("enableType", "Y")
                .param("vendorCode", "a".repeat(21))   // VARCHAR2(20)
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/form"))
            .andExpect(content().string(containsString("바이트를 넘을 수 없습니다")));
    }

    /** 한글은 1자가 3바이트다. 문자 수로 검증하면 저장 단계에서 ORA-12899 가 난다. */
    @Test void koreanNameOverByteLimitIsRejectedWithMessage() throws Exception {
        mvc.perform(post("/companies").with(user(superUser)).with(csrf())
                .param("companyName", "가".repeat(86))  // 258 bytes > 256
                .param("enableType", "Y")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/form"))
            .andExpect(content().string(containsString("바이트를 넘을 수 없습니다")));
    }

    @Test void createRedirectsToDetail() throws Exception {
        CcfaCompany saved = new CcfaCompany(); saved.setIdx(77L);
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("77");
        mvc.perform(post("/companies").with(user(superUser)).with(csrf()).param("companyName", "신규").param("enableType", "Y")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/companies/77"));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/companies").with(user(superUser)).param("companyName", "x")).andExpect(status().isForbidden());
    }

    /** @PathVariable Long 으로 바꿀 수 없는 값(숫자가 아님)은 어떤 자원도 가리키지 않으므로 404. */
    @Test void nonNumericIdIsNotFound() throws Exception {
        mvc.perform(get("/companies/abc").with(user(superUser)))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error/404"));
    }
}
