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
            "CHALLENGE_TERM", "180", "SMTP_PORT", "25",
            "CERT_P1", "ENABLE", "CERT_VERIFY", "no",
            "FIDO_ATTESTCERT_AAID_CHECK", "Y"));
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

    /**
     * 토글의 hidden/checkbox 값이 그 키의 표기여야 한다. 하드코딩된 Y/N 으로 되돌아가면
     * 화면은 멀쩡한데 FIDO 서버가 못 읽는 값이 저장된다.
     */
    @Test void 토글은_키별_표기로_렌더된다() throws Exception {
        String html = mvc.perform(get("/system/settings").session(session).with(user(superUser)))
            .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("name=\"CERT_P1\" value=\"DISABLE\"");
        assertThat(html).contains("name=\"CERT_P1\" value=\"ENABLE\"");
        assertThat(html).contains("name=\"FIDO_ATTESTCERT_AAID_CHECK\" value=\"N\"");
        assertThat(html).contains("name=\"CERT_VERIFY\" value=\"no\"");
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
                .param("SMTP_IP", "10.0.0.1").param("CHALLENGE_TERM", "90"))
            .andExpect(status().is3xxRedirection())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .redirectedUrl("/system/settings"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(service).save(captor.capture());
        assertThat(captor.getValue()).containsEntry("SMTP_IP", "10.0.0.1");
        assertThat(captor.getValue()).containsEntry("CHALLENGE_TERM", "90");
    }

    /**
     * 회귀 테스트. 토글은 hidden("끔")과 체크박스("켬")를 같은 이름으로 함께 보내는데,
     * 컨트롤러의 {@code @RequestParam Map<String,String>} 은 중복 파라미터에서
     * <b>첫 값만</b> 취한다. 따라서 체크박스가 hidden 보다 앞서야 한다 —
     * 순서가 뒤집히면 화면에서 아무리 켜도 항상 "끔"이 저장된다(실제로 그랬다).
     */
    @Test void 토글은_체크박스가_hidden_보다_앞에_온다() throws Exception {
        String html = mvc.perform(get("/system/settings").session(session).with(user(superUser)))
            .andReturn().getResponse().getContentAsString();

        for (FidoSettingKey k : FidoSettingKey.values()) {
            if (k.type() != FidoSettingKey.Type.TOGGLE) continue;
            int checkbox = html.indexOf("name=\"" + k.key() + "\" value=\"" + k.style().on() + "\"");
            int hidden = html.indexOf("name=\"" + k.key() + "\" value=\"" + k.style().off() + "\"");
            assertThat(checkbox).as("%s 체크박스가 렌더되지 않았다", k.key()).isGreaterThanOrEqualTo(0);
            assertThat(hidden).as("%s hidden 이 렌더되지 않았다", k.key()).isGreaterThanOrEqualTo(0);
            assertThat(checkbox).as("%s: 체크박스가 hidden 보다 앞에 와야 한다", k.key()).isLessThan(hidden);
        }
    }

    /** 브라우저가 보내는 형태(체크박스 + hidden 중복)에서 "켬" 값이 서비스까지 도달해야 한다. */
    @Test void 토글을_켜면_켠_값이_서비스로_간다() throws Exception {
        mvc.perform(post("/system/settings").session(session).with(user(superUser)).with(csrf())
                .param("CERT_P1", "ENABLE", "DISABLE")
                .param("CERT_P7", "DISABLE"))
            .andExpect(status().is3xxRedirection());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(service).save(captor.capture());
        assertThat(captor.getValue()).containsEntry("CERT_P1", "ENABLE");
        assertThat(captor.getValue()).containsEntry("CERT_P7", "DISABLE");
    }

    /** 상태를 바꾸는 요청은 CSRF 토큰이 있어야 한다. */
    @Test void CSRF_없는_저장은_거부된다() throws Exception {
        mvc.perform(post("/system/settings").session(session).with(user(superUser))
                .param("SMTP_IP", "10.0.0.1"))
            .andExpect(status().isForbidden());
    }
}
