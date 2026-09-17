package com.crosscert.fidoadmin.signup;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SignupAdminController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class,
         GlobalExceptionHandler.class})
class SignupAdminControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean SignupService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser =
        new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser =
        new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaManager pending() {
        CcfaManager m = new CcfaManager();
        m.setIdx(5L);
        m.setUserId("newbie");
        m.setUserNm("홍길동");
        m.setUserEmail("hong@kb.local");
        m.setUserPhone("010-1234-5678");
        m.setStatus(SignupPolicy.STATUS_PENDING);
        m.setCompanyIdx(SignupPolicy.UNASSIGNED_COMPANY_IDX);
        m.setEtc("신청 사유: 업무 담당자입니다");
        m.setCreatedtime(LocalDateTime.of(2026, 9, 17, 10, 0));
        return m;
    }

    private CcfaCompany company(long idx, String name) {
        CcfaCompany c = new CcfaCompany();
        c.setIdx(idx);
        c.setCompanyName(name);
        return c;
    }

    private void twoCompanies() {
        when(companies.all()).thenReturn(List.of(company(0L, "전역"), company(1L, "KB국민은행")));
    }

    @Test void listShowsPendingApplications() throws Exception {
        when(service.pending()).thenReturn(List.of(pending()));
        twoCompanies();
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("newbie")))
            .andExpect(content().string(containsString("홍길동")))
            .andExpect(content().string(containsString("hong@kb.local")))
            .andExpect(content().string(containsString("업무 담당자입니다")));
    }

    @Test void listShowsEmptyMessageWhenNothingPending() throws Exception {
        when(service.pending()).thenReturn(List.of());
        twoCompanies();
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("승인 대기 중인 신청이 없습니다")));
    }

    /**
     * 승인 화면의 고객사 선택 목록에서 전역(IDX 0)을 제외한다.
     * 옵션 태그 자체를 찾는다. 화면 어딘가의 {@code value="0"} 과 섞이지 않게 하기 위해서다.
     */
    @Test void companySelectExcludesGlobalCompany() throws Exception {
        when(service.pending()).thenReturn(List.of(pending()));
        twoCompanies();
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<option value=\"1\">KB국민은행</option>")))
            .andExpect(content().string(not(containsString("<option value=\"0\""))))
            .andExpect(content().string(not(containsString(">전역</option>"))));
    }

    /** 거절 사유 입력란은 ETC 바이트 여유(약 318바이트)를 넘겨 쓰도록 유도하지 않는다. */
    @Test void rejectReasonInputIsLengthLimited() throws Exception {
        when(service.pending()).thenReturn(List.of(pending()));
        twoCompanies();
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("maxlength=\"100\"")));
    }

    @Test void approveRedirectsWithFlash() throws Exception {
        when(service.approve(5L, 1L)).thenReturn(pending());
        mvc.perform(post("/signups/5/approve").with(user(superUser)).with(csrf())
                .param("companyIdx", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashSuccess", "가입을 승인했습니다."));
        verify(service).approve(5L, 1L);
    }

    /** 서비스가 입력을 거부하면(IllegalArgumentException) 오류 플래시로 돌려준다(500 이 아니다). */
    @Test void approveShowsErrorWhenCompanyIsRejected() throws Exception {
        when(service.approve(5L, 0L))
            .thenThrow(new IllegalArgumentException("전역(IDX 0) 고객사로는 승인할 수 없습니다."));
        mvc.perform(post("/signups/5/approve").with(user(superUser)).with(csrf())
                .param("companyIdx", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashError", "전역(IDX 0) 고객사로는 승인할 수 없습니다."));
    }

    /** 이미 처리된 신청(IllegalStateException)도 같은 방식으로 오류 플래시가 된다. */
    @Test void approveShowsErrorWhenAlreadyProcessed() throws Exception {
        when(service.approve(5L, 1L))
            .thenThrow(new IllegalStateException("이미 처리된 신청입니다(승인대기 상태가 아닙니다): newbie"));
        mvc.perform(post("/signups/5/approve").with(user(superUser)).with(csrf())
                .param("companyIdx", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashError",
                "이미 처리된 신청입니다(승인대기 상태가 아닙니다): newbie"));
    }

    /**
     * 고객사를 고르지 않으면 빈 문자열이 온다. 400(흰 오류 화면)이 아니라 null 로 서비스에 넘겨
     * 서비스의 "고객사를 선택해야 합니다" 거부가 오류 플래시로 보이게 한다.
     */
    @Test void approveWithoutCompanyShowsError() throws Exception {
        when(service.approve(5L, null)).thenThrow(new IllegalArgumentException("고객사를 선택해야 합니다."));
        mvc.perform(post("/signups/5/approve").with(user(superUser)).with(csrf())
                .param("companyIdx", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashError", "고객사를 선택해야 합니다."));
        verify(service).approve(5L, null);
    }

    @Test void rejectRedirectsWithFlash() throws Exception {
        when(service.reject(5L, "소속 확인 불가")).thenReturn(pending());
        mvc.perform(post("/signups/5/reject").with(user(superUser)).with(csrf())
                .param("reason", "소속 확인 불가"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashSuccess", "가입을 거절했습니다."));
        verify(service).reject(5L, "소속 확인 불가");
    }

    @Test void rejectShowsErrorWhenAlreadyProcessed() throws Exception {
        when(service.reject(anyLong(), anyString()))
            .thenThrow(new IllegalStateException("이미 처리된 신청입니다(승인대기 상태가 아닙니다): newbie"));
        mvc.perform(post("/signups/5/reject").with(user(superUser)).with(csrf())
                .param("reason", "소속 확인 불가"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attributeExists("flashError"));
    }

    /** 일반 고객사 계정은 접근할 수 없다. */
    @Test void companyUserIsForbidden() throws Exception {
        mvc.perform(get("/signups").with(user(companyUser)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/signups/5/approve").with(user(companyUser)).with(csrf())
                .param("companyIdx", "1"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/signups/5/reject").with(user(companyUser)).with(csrf())
                .param("reason", "안 됨"))
            .andExpect(status().isForbidden());
        verify(service, never()).approve(anyLong(), anyLong());
        verify(service, never()).reject(anyLong(), anyString());
    }

    @Test void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/signups"))
            .andExpect(status().is3xxRedirection());
        verify(service, never()).pending();
    }
}
