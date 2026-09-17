package com.crosscert.fidoadmin.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SignupController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class,
         GlobalExceptionHandler.class})
class SignupControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean SignupService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    /** 가입 화면은 로그인 없이 열린다. */
    @Test void formIsPublic() throws Exception {
        mvc.perform(get("/signup"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("가입 신청")));
    }

    /** 소속 고객사 선택란을 두지 않는다(로그인 전 화면에 고객사 목록을 노출하지 않는다). */
    @Test void formHasNoCompanySelector() throws Exception {
        mvc.perform(get("/signup"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("name=\"status\""))));
    }

    @Test void validApplicationRedirectsToLogin() throws Exception {
        when(service.apply(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(new CcfaManager());
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local")
                .param("userPhone", "010-0000-0000")
                .param("reason", "업무 담당자입니다"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?signup"));
    }

    /** 폼에 소속·상태를 실어 보내도 서비스는 그 값을 받지 않는다. */
    @Test void injectedCompanyIdxAndStatusAreIgnored() throws Exception {
        when(service.apply(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(new CcfaManager());
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "attacker")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "공격자")
                .param("userEmail", "a@b.local")
                .param("companyIdx", "0")
                .param("status", "활성"))
            .andExpect(status().is3xxRedirection());

        ArgumentCaptor<String> userId = ArgumentCaptor.forClass(String.class);
        verify(service).apply(userId.capture(), anyString(), anyString(), anyString(), any(), any());
        assertThat(userId.getValue()).isEqualTo("attacker");
        // SignupForm 에 companyIdx·status 필드가 없으므로 주입 통로 자체가 없다
        assertThat(SignupForm.class.getDeclaredFields())
            .noneMatch(f -> f.getName().equals("companyIdx") || f.getName().equals("status"));
    }

    @Test void rejectsWeakPassword() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "weak")
                .param("passwordConfirm", "weak")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "password"));
        verify(service, never()).apply(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test void rejectsConfirmMismatch() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company9999!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "passwordConfirm"));
    }

    /**
     * 중복 아이디는 그 자리에서 폼 오류로 알려준다. 계정 열거를 허용하는 대신
     * 사용자가 왜 신청이 안 됐는지 알 수 있게 한 의도된 선택이다(설계서 6.1).
     */
    @Test void rejectsDuplicateUserId() throws Exception {
        when(service.existsUserId("kbadmin")).thenReturn(true);
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "kbadmin")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "userId"));
        verify(service, never()).apply(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    /** 저장 직전 경합으로 중복이 났을 때도 폼 오류로 돌려준다(500 이 아니다). */
    @Test void handlesRaceConditionDuplicate() throws Exception {
        when(service.existsUserId("newbie")).thenReturn(false);
        when(service.apply(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenThrow(new DataIntegrityViolationException("CCFA_MANAGER newbie 은(는) 이미 존재합니다"));
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "userId"));
    }

    /**
     * 선택 항목은 비워둘 수 있고, 빈 값은 null 로 서비스에 넘어간다.
     * 아울러 비밀번호는 평문이 아니라 인코딩된 값으로 넘어가야 한다.
     */
    @Test void optionalFieldsAreNullAndPasswordIsEncoded() throws Exception {
        when(service.apply(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(new CcfaManager());
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local")
                .param("userPhone", "")
                .param("reason", "   "))
            .andExpect(status().is3xxRedirection());

        ArgumentCaptor<String> pw = ArgumentCaptor.forClass(String.class);
        verify(service).apply(anyString(), pw.capture(), anyString(), anyString(), any(), any());
        assertThat(pw.getValue()).isNotEqualTo("Company1234!");   // 평문이 그대로 가면 안 된다
        verify(service).apply("newbie", pw.getValue(), "홍길동", "hong@kb.local", null, null);
    }

    /** 이메일 형식이 아니면 거부한다. */
    @Test void rejectsMalformedEmail() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "not-an-email"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "userEmail"));
        verify(service, never()).apply(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    /**
     * 길이 제한은 문자 수가 아니라 바이트 수로 건다. 한글 1자가 3바이트인 스키마라
     * 문자 수로 재면 컬럼을 넘겨 ORA-12899 로 500 이 난다.
     */
    @Test void rejectsNameOverByteLimit() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "가".repeat(17))   // 51바이트 > 50
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "userNm"));
        verify(service, never()).apply(anyString(), anyString(), anyString(), anyString(), any(), any());
    }
}
