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
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.service.ManagerService;
import com.crosscert.fidoadmin.manager.service.SuperManagerService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /managers/super 는 ManagerController 의 "/managers/{id}" 와 URL 이 겹칠 수 있어 보인다.
 * 이 슬라이스는 두 컨트롤러를 함께 등록해 Spring 이 실제로 어느 쪽으로 라우팅하는지
 * (규칙에 의존하지 않고) 확인한다.
 */
@WebMvcTest(controllers = {SuperManagerController.class, ManagerController.class})
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class SuperManagerControllerWebTest {

    static final String PW_HASH = "a".repeat(64);

    @Autowired MockMvc mvc;
    @Autowired SelectedTenant selected;
    @MockitoBean SuperManagerService service;
    @MockitoBean ManagerService managerService;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 5L, "KB", true, true);

    private CcfaManager manager(long idx, String userId) {
        CcfaManager m = new CcfaManager(); m.setIdx(idx); m.setUserId(userId); m.setUserPw(PW_HASH);
        m.setUserNm("슈퍼관리자"); m.setCompanyIdx(0L); m.setStatus("활성"); m.setLogin("OFF-LINE");
        return m;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/managers/super").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /**
     * 시스템 영역이므로 고객사 선택 없이 열린다. TenantSelectionInterceptor 는 TENANT 영역만
     * 가로채는데, MenuRegistry.areaOf("/managers/super") 가 SYSTEM 이어야 이 테스트가 통과한다.
     */
    @Test void 미선택_SUPER_도_열_수_있다() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/managers/super").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/super/list"));
    }

    /**
     * 라우팅 확인: "/managers/super" 는 SuperManagerController 로 가야 한다.
     * ManagerController 의 "/managers/{id}" 로 갔다면 {id}=Long 변환에 실패해 404가 되거나,
     * (막았어야 할) ManagerService 가 호출된다. 여기서는 뷰 이름으로 목적지를 확정한다.
     */
    @Test void 슈퍼관리자_경로는_SuperManagerController_로_라우팅된다() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(manager(9L, "superadmin"))));
        mvc.perform(get("/managers/super").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/super/list"))
            .andExpect(content().string(containsString("superadmin")));
        verify(managerService, org.mockito.Mockito.never()).search(any(), any());
    }

    /** 민감 컬럼 USER_PW 는 목록·상세 어디에도 실리지 않는다. */
    @Test void listNeverExposesPasswordHash() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(manager(9L, "superadmin"))));
        mvc.perform(get("/managers/super").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("superadmin")))
            .andExpect(content().string(not(containsString(PW_HASH))));
    }

    @Test void detailShowsLockStateAndHidesPasswordHash() throws Exception {
        when(service.get(9L)).thenReturn(manager(9L, "superadmin"));
        CcfaManagerPwPolicy p = new CcfaManagerPwPolicy(); p.setAccountLock("Y"); p.setPwFailCnt(3L);
        when(service.lockState("superadmin")).thenReturn(Optional.of(p));
        mvc.perform(get("/managers/super/9").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/super/detail"))
            .andExpect(content().string(containsString("잠김")))
            .andExpect(content().string(not(containsString(PW_HASH))));
    }

    @Test void createWithoutPasswordShowsFormWithMessage() throws Exception {
        mvc.perform(post("/managers/super").with(user(superUser)).with(csrf())
                .param("userId", "newsuper").param("companyIdx", "0").param("status", "활성"))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/super/form"))
            .andExpect(content().string(containsString("비밀번호는 필수입니다.")));
    }

    @Test void createEncodesPasswordAndRedirects() throws Exception {
        CcfaManager saved = manager(20L, "newsuper");
        when(service.create(any())).thenReturn(saved);
        when(service.idOf(any())).thenReturn("20");
        mvc.perform(post("/managers/super").with(user(superUser)).with(csrf())
                .param("userId", "newsuper").param("companyIdx", "0").param("status", "활성")
                .param("password", "Secret1234!").param("passwordConfirm", "Secret1234!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/super/20"));
    }

    @Test void unlockRedirectsToDetailWithFlash() throws Exception {
        mvc.perform(post("/managers/super/9/unlock").with(user(superUser)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/super/9"))
            .andExpect(flash().attribute("flashSuccess", "잠금이 해제되었습니다."));
        verify(service).unlock(9L);
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/managers/super/9/unlock").with(user(superUser))).andExpect(status().isForbidden());
    }

    /**
     * 자기 자신은 삭제할 수 없다. 이 화면에서 더 중요한 규칙이다 — 복구 경로가 없다.
     * 서비스가 IllegalStateException 을 던지면 컨트롤러가 500 대신 flashError 로 되돌린다.
     */
    @Test void 자기_자신은_삭제할_수_없다() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("자기 자신은 삭제할 수 없습니다."))
            .when(service).delete(1L);
        mvc.perform(post("/managers/super/1/delete").with(user(superUser)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/super/1"))
            .andExpect(flash().attribute("flashError", "자기 자신은 삭제할 수 없습니다."));
    }
}
