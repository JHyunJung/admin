package com.crosscert.fidoadmin.manager.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.service.ManagerService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@WebMvcTest(controllers = ManagerController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class ManagerControllerWebTest {

    static final String PW_HASH = "a".repeat(64);

    @Autowired MockMvc mvc;
    @Autowired SelectedTenant selected;
    @MockitoBean ManagerService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    /**
     * /managers 는 TENANT 영역이라 TenantSelectionInterceptor(Task 6)가 미선택 SUPER 를
     * /select-tenant 로 돌려보낸다. 이 클래스의 관심사는 운영자 CRUD 이므로,
     * 매 테스트마다 세션에 테넌트를 미리 선택해 둔다.
     */
    MockHttpSession session;

    @BeforeEach void selectTenant() {
        session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            selected.select(9L);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private CcfaManager manager(long idx, String userId) {
        CcfaManager m = new CcfaManager(); m.setIdx(idx); m.setUserId(userId); m.setUserPw(PW_HASH);
        m.setUserNm("KB운영자"); m.setCompanyIdx(1L); m.setStatus("활성"); m.setLogin("OFF-LINE");
        return m;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/managers").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 민감 컬럼 USER_PW 는 목록·상세 어디에도 실리지 않는다(설계 2.2). */
    @Test void listNeverExposesPasswordHash() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(manager(2L, "kbadmin"))));
        mvc.perform(get("/managers").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/list"))
            .andExpect(content().string(containsString("kbadmin")))
            .andExpect(content().string(not(containsString(PW_HASH))));
    }

    @Test void detailNeverExposesPasswordHashAndShowsLockState() throws Exception {
        when(service.get(2L)).thenReturn(manager(2L, "kbadmin"));
        CcfaManagerPwPolicy p = new CcfaManagerPwPolicy(); p.setAccountLock("Y"); p.setPwFailCnt(5L);
        when(service.lockState("kbadmin")).thenReturn(Optional.of(p));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/managers/2").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/detail"))
            .andExpect(content().string(containsString("잠김")))
            .andExpect(content().string(containsString("잠금 해제")))
            .andExpect(content().string(not(containsString(PW_HASH))));
    }

    @Test void createWithoutPasswordShowsFormWithMessage() throws Exception {
        mvc.perform(post("/managers").session(session).with(user(superUser)).with(csrf())
                .param("userId", "newop").param("status", "활성"))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/form"))
            .andExpect(content().string(containsString("비밀번호는 필수입니다.")));
    }

    @Test void passwordMismatchShowsMessage() throws Exception {
        mvc.perform(post("/managers").session(session).with(user(superUser)).with(csrf())
                .param("userId", "newop").param("status", "활성")
                .param("password", "Secret1234!").param("passwordConfirm", "Other1234!"))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/form"))
            .andExpect(content().string(containsString("비밀번호 확인이 일치하지 않습니다.")));
    }

    /** 등록 시 USER_PW 는 SHA-256 hex 로 인코딩되어 서비스로 간다(평문이 아니다). */
    @Test void createEncodesPasswordAndRedirects() throws Exception {
        CcfaManager saved = manager(10L, "newop");
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("10");
        mvc.perform(post("/managers").session(session).with(user(superUser)).with(csrf())
                .param("userId", "newop").param("status", "활성")
                .param("password", "Secret1234!").param("passwordConfirm", "Secret1234!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/10"));
        ArgumentCaptor<CcfaManager> captor = ArgumentCaptor.forClass(CcfaManager.class);
        verify(service).create(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getUserPw())
            .isEqualTo(com.crosscert.fidoadmin.auth.Sha256PasswordEncoder.sha256Hex("Secret1234!"));
    }

    @Test void createWithShortPasswordShowsSizeMessage() throws Exception {
        mvc.perform(post("/managers").session(session).with(user(superUser)).with(csrf())
                .param("userId", "newop").param("status", "활성")
                .param("password", "short1!").param("passwordConfirm", "short1!"))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/form"))
            .andExpect(content().string(containsString("비밀번호는 8자 이상 64자 이하여야 합니다.")));
        verify(service, org.mockito.Mockito.never()).create(any());
    }

    @Test void createWithoutSpecialCharShowsPolicyMessage() throws Exception {
        mvc.perform(post("/managers").session(session).with(user(superUser)).with(csrf())
                .param("userId", "newop").param("status", "활성")
                .param("password", "abcdefgh1").param("passwordConfirm", "abcdefgh1"))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/form"))
            .andExpect(content().string(containsString("영문, 숫자, 특수문자를 모두 포함해야 합니다.")));
        verify(service, org.mockito.Mockito.never()).create(any());
    }

    /** 수정 시 비밀번호를 비워두면(변경하지 않으면) 정책 검사를 건너뛰고 그대로 저장된다. */
    @Test void editWithBlankPasswordStillSucceeds() throws Exception {
        mvc.perform(post("/managers/2").session(session).with(user(superUser)).with(csrf())
                .param("userId", "kbadmin").param("status", "활성"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/2"));
    }

    /**
     * Task 10 부터 목록에는 고객사 컬럼이 없다(세션이 테넌트를 정한다).
     * companyIdx 를 알 수 없는 이름 조회로도 "null" 이 새지 않는지는 여전히 지킨다.
     */
    @Test void listRendersRowWithoutLeakingNullForUnknownCompany() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        CcfaManager m = manager(2L, "kbadmin"); m.setCompanyIdx(5L);
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(m)));
        mvc.perform(get("/managers").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(">null<"))));
    }

    /**
     * 승인대기 행의 수정 화면은 상태를 고를 수 없게 보여주고, 가입 승인 화면으로 안내해야 한다.
     * 선택지가 활성/비활성 뿐이라 승인대기를 표현하지 못하고, 그대로 두면 브라우저가 활성을 고른다.
     */
    @Test void editFormShowsStatusReadOnlyForPendingRow() throws Exception {
        CcfaManager m = manager(2L, "applicant");
        m.setStatus(com.crosscert.fidoadmin.signup.SignupPolicy.STATUS_PENDING);
        when(service.get(2L)).thenReturn(m);

        mvc.perform(get("/managers/2/edit").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/form"))
            .andExpect(content().string(containsString("가입 승인")))
            .andExpect(content().string(not(containsString("<option value=\"활성\""))))
            .andExpect(content().string(not(containsString("<option value=\"비활성\""))));
    }

    /** 정상 상태 행의 수정 화면은 기존대로 활성/비활성을 고를 수 있어야 한다. */
    @Test void editFormKeepsStatusSelectForNormalRow() throws Exception {
        when(service.get(2L)).thenReturn(manager(2L, "kbadmin"));

        mvc.perform(get("/managers/2/edit").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<option value=\"활성\"")))
            .andExpect(content().string(containsString("<option value=\"비활성\"")));
    }

    /**
     * 템플릿을 우회해 상태를 실어 보낸 POST 도 활성화되면 안 된다. 서비스가 거부한 결과가
     * 500 이 아니라 안내 메시지로 화면에 돌아와야 한다.
     */
    @Test void craftedPostCannotActivatePendingRow() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("가입 승인 화면에서 처리해야 합니다."))
            .when(service).update(org.mockito.ArgumentMatchers.eq(2L), any());

        mvc.perform(post("/managers/2").session(session).with(user(superUser)).with(csrf())
                .param("userId", "applicant").param("status", "활성"))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/manager/form"))
            .andExpect(content().string(containsString("가입 승인")));
    }

    @Test void unlockRedirectsToDetailWithFlash() throws Exception {
        mvc.perform(post("/managers/2/unlock").session(session).with(user(superUser)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/2"))
            .andExpect(flash().attribute("flashSuccess", "잠금이 해제되었습니다."));
        verify(service).unlock(2L);
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/managers/2/unlock").session(session).with(user(superUser))).andExpect(status().isForbidden());
    }
}
