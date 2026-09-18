package com.crosscert.fidoadmin.system.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.system.service.FidoSettingService;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@WebMvcTest(controllers = FidoSettingController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class,
         GlobalExceptionHandler.class, TenantContext.class, SelectedTenant.class})
class FidoSettingControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired SelectedTenant selected;
    @MockitoBean FidoSettingService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser =
        new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser =
        new ManagerUserDetails(2L, "kbadmin", null, "국민", 5L, "KB", true, true);

    MockHttpSession session;

    @BeforeEach void setUp() {
        when(service.load()).thenReturn(Map.of(
            "CHALLENGE_EXPIRE_SECONDS", "60", "SMTP_PORT", "25",
            "AUTH_RESPONSE_OPTIONS", "PKCS1,PUBLIC_KEY"));
        session = selecting(9L);
    }

    /** 세션 스코프 빈에 선택값을 넣는다. 이 슬라이스에는 TenantSelectionController 가 없다. */
    private MockHttpSession selecting(long companyIdx) {
        MockHttpSession s = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(s);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            selected.select(companyIdx);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        return s;
    }

    @Test void 화면이_열린다() throws Exception {
        mvc.perform(get("/system/settings").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/settings/form"))
            .andExpect(content().string(Matchers.containsString("FIDO 서버 설정")));
    }

    /** 키 이름이 운영 서버와 맞춰지지 않았다는 경고를 숨기지 않는다. */
    @Test void 경고_배너가_보인다() throws Exception {
        mvc.perform(get("/system/settings").session(session).with(user(superUser)))
            .andExpect(content().string(Matchers.containsString("아직 맞춰지지 않았습니다")));
    }

    @Test void 세_섹션이_모두_그려진다() throws Exception {
        mvc.perform(get("/system/settings").session(session).with(user(superUser)))
            .andExpect(content().string(Matchers.containsString("인증서 설정")))
            .andExpect(content().string(Matchers.containsString("FIDO 부가기능 설정")))
            .andExpect(content().string(Matchers.containsString("알림메일 설정")));
    }

    /** SUPER 전용 화면이다. COMPANY 계정은 URL 로도 못 연다. */
    @Test void COMPANY_계정은_접근할_수_없다() throws Exception {
        mvc.perform(get("/system/settings").with(user(companyUser)))
            .andExpect(status().isForbidden());
    }

    @Test void 저장하면_서비스로_전달되고_되돌아온다() throws Exception {
        mvc.perform(post("/system/settings").session(session).with(user(superUser)).with(csrf())
                .param("SMTP_HOST", "10.0.0.1").param("CHALLENGE_EXPIRE_SECONDS", "90"))
            .andExpect(status().is3xxRedirection())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .redirectedUrl("/system/settings"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(service).save(captor.capture());
        assertThat(captor.getValue()).containsEntry("SMTP_HOST", "10.0.0.1");
        assertThat(captor.getValue()).containsEntry("CHALLENGE_EXPIRE_SECONDS", "90");
    }

    /** 상태를 바꾸는 요청은 CSRF 토큰이 있어야 한다. */
    @Test void CSRF_없는_저장은_거부된다() throws Exception {
        mvc.perform(post("/system/settings").session(session).with(user(superUser))
                .param("SMTP_HOST", "10.0.0.1"))
            .andExpect(status().isForbidden());
    }
}
