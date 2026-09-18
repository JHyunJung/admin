package com.crosscert.fidoadmin.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .content().string(org.hamcrest.Matchers.containsString("영문, 숫자, 특수문자를 모두 포함해야 합니다.")));
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

    @Test void rejectsConfirmMismatch() throws Exception {
        mvc.perform(change("NewPass5678!", "Other5678!"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));
        verify(service, never()).change(any(), any(), any());
    }
}
