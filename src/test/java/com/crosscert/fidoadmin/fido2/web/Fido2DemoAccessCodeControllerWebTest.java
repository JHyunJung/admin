package com.crosscert.fidoadmin.fido2.web;

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
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import com.crosscert.fidoadmin.fido2.service.Fido2DemoAccessCodeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = Fido2DemoAccessCodeController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class Fido2DemoAccessCodeControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean Fido2DemoAccessCodeService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private Fido2DemoAccessCode seedCode() {
        Fido2DemoAccessCode c = new Fido2DemoAccessCode();
        c.setAccesscode("DEMO-0001"); c.setVendorname("벤더A"); c.setStarttime(1767225600L); c.setEndtime(1798761600L);
        c.setStatus("E"); c.setNote("데모용");
        return c;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/fido2/demo-access-codes").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** epoch 초 1767225600 은 서울 2026-01-01 09:00 으로 보여야 한다. */
    @Test void listShowsEpochAsSeoulDateTime() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("accesscode"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(seedCode())));
        mvc.perform(get("/fido2/demo-access-codes").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/list"))
            .andExpect(content().string(containsString("DEMO-0001")))
            .andExpect(content().string(containsString("2026-01-01 09:00")));
    }

    @Test void detailShowsRawEpochToo() throws Exception {
        when(service.get("DEMO-0001")).thenReturn(seedCode());
        mvc.perform(get("/fido2/demo-access-codes/DEMO-0001").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/detail"))
            .andExpect(content().string(containsString("1767225600")))
            .andExpect(content().string(containsString("2026-01-01 09:00")));
    }

    @Test void endBeforeStartShowsFormWithMessage() throws Exception {
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "DEMO-0003").param("status", "E")
                .param("starttime", "2026-01-02T00:00").param("endtime", "2026-01-01T00:00"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/form"))
            .andExpect(content().string(containsString("종료일시는 시작일시보다 빠를 수 없습니다.")));
    }

    @Test void blankAccessCodeShowsFormAgain() throws Exception {
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "").param("status", "E"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/form"));
    }

    @Test void createRedirectsToDetailByAccessCode() throws Exception {
        Fido2DemoAccessCode saved = new Fido2DemoAccessCode(); saved.setAccesscode("DEMO-0003");
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("DEMO-0003");
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "DEMO-0003").param("vendorname", "벤더C").param("status", "E")
                .param("starttime", "2026-01-01T09:00").param("endtime", "2026-12-31T23:59"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fido2/demo-access-codes/DEMO-0003"));
    }
}
