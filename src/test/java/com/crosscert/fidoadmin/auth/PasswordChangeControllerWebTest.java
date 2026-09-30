package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 비밀번호 변경 화면의 형식 검증이 어떤 입력을 막는지 고정한다.
 *
 * <p>이 화면의 형식 규칙은 어노테이션({@code @Size}/{@code @Pattern})으로 걸려 있고,
 * 그 속성은 {@code PasswordPolicy} 의 상수를 참조한다. 어노테이션 경로에는 지금까지
 * 테스트가 없어서, 규칙이 바뀌어도 아무것도 깨지지 않았다. 여기서 실제 거부 동작을 고정한다.
 *
 * <p><b>이 테스트가 보장하지 않는 것</b>: 규칙의 선언이 한 곳뿐인지는 검사하지 않는다.
 * 자바는 {@code static final} 상수를 컴파일 시 값으로 박아 넣으므로, 이 폼이 상수를 참조하든
 * 리터럴을 다시 적든 동작은 같고 테스트도 똑같이 통과한다(tasks/lessons.md).
 */
@WebMvcTest(controllers = PasswordChangeController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class PasswordChangeControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean PasswordChangeService service;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean LoginSuccessHandler success;
    @MockitoBean LoginFailureHandler failure;
    @MockitoBean AppLogoutSuccessHandler logout;
    @MockitoBean ManagerUserDetailsService uds;

    ManagerUserDetails me = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder change(String next, String confirm) {
        return post("/me/password").with(user(me)).with(csrf())
            .param("currentPassword", "Company1234!")
            .param("newPassword", next)
            .param("confirmPassword", confirm);
    }

    @Test void successClearsExpiredMarkAndShowsNoticeBefore() throws Exception {
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90);
        mvc.perform(get("/me/password").session(session).with(user(me)))
            .andExpect(content().string(containsString("비밀번호를 변경한 지 90일이 지났습니다. 새 비밀번호로 변경하세요.")));
        mvc.perform(change("NewPass5678!", "NewPass5678!").session(session))
            .andExpect(status().is3xxRedirection());
        assertThat(session.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull();
    }

    /** 만료 세션에서 잘못 입력해 폼이 다시 그려져도 안내 문구가 남는다. */
    @Test void noticeStaysWhenFormRerendersWithErrors() throws Exception {
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90);
        mvc.perform(change("Ab1!", "Ab1!").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("90일이 지났습니다")));
        assertThat(session.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isEqualTo(90);
    }

    /**
     * 테넌트를 고르지 않은 SUPER 가 만료 표시를 단 채 변경 화면을 열고 저장할 수 있어야 한다.
     * /me/password 는 PERSONAL 영역이라 TenantSelectionInterceptor 가 /select-tenant 로 돌려보내지 않는다.
     * 돌려보내면 만료 인터셉터가 다시 /me/password 로 보내 무한 리다이렉트에 갇힌다.
     */
    @Test void unselectedSuperCanReachPasswordPageWhileExpired() throws Exception {
        ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90);
        mvc.perform(get("/me/password").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("auth/password"));
        mvc.perform(post("/me/password").session(session).with(user(superUser)).with(csrf())
                .param("currentPassword", "Company1234!")
                .param("newPassword", "NewPass5678!")
                .param("confirmPassword", "NewPass5678!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
        assertThat(session.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isNull();
    }

    /**
     * 배선 확인: 만료 인터셉터가 WebMvcConfig 에 등록돼 있고 테넌트 선택 인터셉터보다 먼저 돈다.
     * 테넌트 미선택 SUPER 가 테넌트 화면(/)을 열면 /select-tenant 가 아니라 /me/password 로 간다.
     */
    @Test void expiredSessionIsRedirectedToPasswordPageBeforeTenantSelection() throws Exception {
        ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90);
        mvc.perform(get("/").session(session).with(user(superUser)))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/me/password"));
        mvc.perform(get("/me/password").session(session).with(user(superUser)))
            .andExpect(status().isOk());
    }

    @Test void acceptsPasswordMeetingPolicy() throws Exception {
        mvc.perform(change("NewPass5678!", "NewPass5678!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
        verify(service).change("kbadmin", "Company1234!", "NewPass5678!");
    }

    /** 8자 미만은 @Size 가 막는다. */
    @Test void rejectsTooShortPassword() throws Exception {
        mvc.perform(change("Ab1!", "Ab1!"))
            .andExpect(status().isOk())
            .andExpect(view().name("auth/password"))
            .andExpect(model().attributeHasFieldErrors("form", "newPassword"));
        verify(service, never()).change(any(), any(), any());
    }

    /** 64자 초과도 @Size 가 막는다(경계: 65자). */
    @Test void rejectsTooLongPassword() throws Exception {
        String tooLong = "A1!" + "a".repeat(62); // 65자
        mvc.perform(change(tooLong, tooLong))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "newPassword"));
        verify(service, never()).change(any(), any(), any());
    }

    /** 특수문자가 없으면 @Pattern 이 막고, 안내 문구는 다른 화면과 같다. */
    @Test void rejectsPasswordWithoutSpecialChar() throws Exception {
        mvc.perform(change("Abcdefg123", "Abcdefg123"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "newPassword"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .content().string(org.hamcrest.Matchers.containsString(com.crosscert.fidoadmin.common.PasswordPolicy.MESSAGE)));
        verify(service, never()).change(any(), any(), any());
    }

    @Test void rejectsNewPasswordWithoutUppercase() throws Exception {
        mvc.perform(change("newpass5678!", "newpass5678!"))
            .andExpect(status().isOk())
            .andExpect(view().name("auth/password"))
            .andExpect(model().attributeHasFieldErrors("form", "newPassword"));
        verify(service, never()).change(any(), any(), any());
    }

    @Test void rejectsPasswordWithoutDigit() throws Exception {
        mvc.perform(change("Abcdefgh!!", "Abcdefgh!!"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "newPassword"));
        verify(service, never()).change(any(), any(), any());
    }

    @Test void rejectsPasswordWithoutLetter() throws Exception {
        mvc.perform(change("12345678!!", "12345678!!"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "newPassword"));
        verify(service, never()).change(any(), any(), any());
    }

    /** 경계값 8자·64자는 통과해야 한다(길이 검사가 한 칸 어긋나지 않았는지). */
    @Test void acceptsBoundaryLengths() throws Exception {
        mvc.perform(change("Abcde12!", "Abcde12!")) // 8자
            .andExpect(status().is3xxRedirection());
        String max = "A1!" + "a".repeat(61); // 64자
        mvc.perform(change(max, max))
            .andExpect(status().is3xxRedirection());
    }

    @Test void samePasswordErrorLandsOnNewPasswordAndKeepsExpiredFlag() throws Exception {
        org.mockito.Mockito.doThrow(new SamePasswordException("현재 비밀번호와 다른 비밀번호를 입력하세요."))
            .when(service).change(any(), any(), any());
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(PasswordExpiredInterceptor.SESSION_ATTR, 90);
        mvc.perform(change("Company1234!", "Company1234!").session(session))
            .andExpect(status().isOk())
            .andExpect(view().name("auth/password"))
            .andExpect(model().attributeHasFieldErrors("form", "newPassword"))
            .andExpect(content().string(containsString("90일이 지났습니다")));
        assertThat(session.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR)).isEqualTo(90);
    }

    @Test void rejectsConfirmMismatch() throws Exception {
        mvc.perform(change("NewPass5678!", "Other5678!"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));
        verify(service, never()).change(any(), any(), any());
    }
}
