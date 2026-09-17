package com.crosscert.fidoadmin.system.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import com.crosscert.fidoadmin.system.service.MenuService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MenuController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class})
class MenuControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean MenuService service;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaMenu menu(long idx, String name, long parent) {
        CcfaMenu m = new CcfaMenu(); m.setIdx(idx); m.setMenuName(name); m.setMenuCode("C" + idx); m.setMenuParentIdx(parent);
        m.setVisible("true"); m.setOpenType("open"); m.setStatistics("N"); m.setReadonly("N");
        return m;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/menus").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 목록은 부모 IDX 대신 부모 이름을 보여준다. */
    @Test void listRendersRowsWithParentName() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.allForSelect()).thenReturn(List.of(menu(1L, "FIDO", 0L), menu(2L, "앱 ID", 1L)));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(menu(1L, "FIDO", 0L), menu(2L, "앱 ID", 1L))));
        mvc.perform(get("/system/menus").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/list"))
            .andExpect(content().string(containsString("앱 ID")))
            .andExpect(content().string(containsString("최상위")))
            .andExpect(content().string(containsString("/system/menus/2")));
    }

    /** 수정 폼의 부모 select 에는 자기 자신이 없고 "최상위" 는 있다. */
    @Test void editFormExcludesSelfFromParentSelect() throws Exception {
        when(service.get(2L)).thenReturn(menu(2L, "앱 ID", 1L));
        when(service.allForSelect()).thenReturn(List.of(menu(1L, "FIDO", 0L), menu(2L, "앱 ID", 1L)));
        mvc.perform(get("/system/menus/2/edit").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/form"))
            .andExpect(content().string(containsString("최상위")))
            .andExpect(content().string(containsString("value=\"1\"")))
            .andExpect(content().string(not(containsString("value=\"2\">#2 앱 ID"))));
    }

    @Test void blankNameShowsFormAgain() throws Exception {
        when(service.allForSelect()).thenReturn(List.of());
        mvc.perform(post("/system/menus").with(user(superUser)).with(csrf())
                .param("menuName", "").param("menuParentIdx", "0").param("visible", "true")
                .param("openType", "open").param("statistics", "N").param("readonly", "N"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/menus/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(menu(9L, "신규", 0L));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/system/menus").with(user(superUser)).with(csrf())
                .param("menuName", "신규").param("menuCode", "NEW").param("menuParentIdx", "0").param("menuSeq", "5")
                .param("visible", "true").param("openType", "open").param("statistics", "N").param("readonly", "N"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/menus/9"));
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/system/menus").with(user(superUser)).param("menuName", "x")).andExpect(status().isForbidden());
    }
}
