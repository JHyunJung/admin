package com.crosscert.fidoadmin.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.List;
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

        // 서비스에 실제로 넘어간 인자 전체를 잡아, 공격자가 실은 값이 어느 자리에도
        // 없음을 확인한다. 필드 이름을 보는 검사와 달리 이름을 바꿔도, 다른 바인딩
        // 경로로 들어와도 잡힌다.
        ArgumentCaptor<String> userId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userNm = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userEmail = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPhone = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(service).apply(userId.capture(), password.capture(), userNm.capture(),
            userEmail.capture(), userPhone.capture(), reason.capture());
        assertThat(userId.getValue()).isEqualTo("attacker");
        assertThat(List.of(userId, password, userNm, userEmail, userPhone, reason))
            .extracting(ArgumentCaptor::getValue)
            .doesNotContain("0", "활성");
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

    /**
     * 앞뒤 공백이 붙은 아이디는 공백을 뗀 형태로 저장돼야 한다.
     *
     * <p>Spring Security 의 UsernamePasswordAuthenticationFilter 가 로그인 때 username 을
     * trim 하므로, " newbie " 로 저장된 계정은 승인을 받아도 영영 로그인되지 않는다.
     * 신청은 302 로 성공하고 승인자에게도 정상으로 보이므로 화면에서는 진단이 불가능하다.
     *
     * <p>중복 검사와 저장이 같은 값을 봤는지도 함께 고정한다. 한쪽만 trim 하면
     * 이 결함이 더 찾기 어려운 형태로 되살아난다.
     */
    @Test void trimsSurroundingWhitespaceFromUserId() throws Exception {
        when(service.apply(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(new CcfaManager());
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "  newbie  ")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?signup"));

        // 저장된 값: 로그인 경로가 나중에 찾아볼 바로 그 형태여야 한다.
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(service).apply(stored.capture(), anyString(), anyString(), anyString(), any(), any());
        assertThat(stored.getValue()).isEqualTo("newbie");

        // 중복 검사가 본 값: 저장된 값과 같아야 한다(두 경로가 갈라지면 안 된다).
        ArgumentCaptor<String> checked = ArgumentCaptor.forClass(String.class);
        verify(service).existsUserId(checked.capture());
        assertThat(checked.getValue()).isEqualTo(stored.getValue());
    }

    /**
     * 공백만 다른 중복 아이디도 중복으로 잡혀야 한다. 한쪽에서만 trim 하면
     * 존재 검사가 " kbadmin " 으로 조회해 빈 결과를 보고 그냥 통과시킨다.
     */
    @Test void detectsDuplicateDespiteSurroundingWhitespace() throws Exception {
        when(service.existsUserId("kbadmin")).thenReturn(true);   // DB 에는 공백 없이 들어 있다
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "  kbadmin  ")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "userId"));
        verify(service, never()).apply(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    /**
     * 공백뿐인 아이디는 필수 입력 오류다. 세터에서 공백을 떼면 빈 문자열이 되고
     * {@code @NotBlank} 가 그대로 잡는다(검증을 따로 더하지 않았다는 근거).
     */
    @Test void rejectsWhitespaceOnlyUserId() throws Exception {
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "    ")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "userId"));
        verify(service, never()).apply(anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    /** 이름·이메일·전화·사유도 공백을 떼어 저장한다. */
    @Test void trimsSurroundingWhitespaceFromOtherFields() throws Exception {
        when(service.apply(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(new CcfaManager());
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "  홍길동  ")
                .param("userEmail", "  hong@kb.local  ")
                .param("userPhone", "  010-0000-0000  ")
                .param("reason", "  업무 담당자입니다  "))
            .andExpect(status().is3xxRedirection());

        verify(service).apply(eq("newbie"), anyString(), eq("홍길동"), eq("hong@kb.local"),
            eq("010-0000-0000"), eq("업무 담당자입니다"));
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

    /**
     * 신청 사유만 제한이 컬럼 폭과 다르다. ETC 는 2048바이트지만 "신청 사유: " 접두와
     * 나중에 덧붙는 거절 사유의 여유를 남겨 1700바이트로 잡았다(설계서 6.1).
     * 2048 로 "고쳐지는" 것을 막기 위해 이 값을 고정한다. 한글로 재야 문자 수와
     * 바이트 수의 차이가 실제로 드러난다(한글 1자 = 3바이트).
     */
    @Test void rejectsReasonOverByteLimit() throws Exception {
        String reason = "사".repeat(567);   // 1701바이트 > 1700, 그러나 문자 수로는 567자
        assertThat(reason.length()).isLessThan(1700);
        mvc.perform(post("/signup").with(csrf())
                .param("userId", "newbie")
                .param("password", "Company1234!")
                .param("passwordConfirm", "Company1234!")
                .param("userNm", "홍길동")
                .param("userEmail", "hong@kb.local")
                .param("reason", reason))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "reason"));
        verify(service, never()).apply(anyString(), anyString(), anyString(), anyString(), any(), any());
    }
}
