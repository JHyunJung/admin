package com.crosscert.fidoadmin.system.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.crosscert.fidoadmin.system.entity.CcfaFields;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.service.FieldService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = FieldController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class FieldControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean FieldService service;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaFields field(long idx, Long optionIdx) {
        CcfaFields f = new CcfaFields(); f.setIdx(idx); f.setFieldTable("APPID"); f.setFieldName("STATUS"); f.setFieldType("select");
        f.setFieldTitle("상태"); f.setPk(0L); f.setFk(0L); f.setEditable(1L); f.setOptionIdx(optionIdx);
        return f;
    }
    private CcfaOption option(long idx, String name) { CcfaOption o = new CcfaOption(); o.setIdx(idx); o.setOptionName(name); return o; }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/fields").with(user(companyUser))).andExpect(status().isForbidden());
    }

    /** 목록은 OPTION_IDX 대신 코드 그룹 이름을 보여준다. */
    @Test void listRendersRowsWithOptionName() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.options()).thenReturn(List.of(option(1L, "STATUS_GROUP")));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(field(1L, 1L))));
        mvc.perform(get("/system/fields").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/list"))
            .andExpect(content().string(containsString("APPID")))
            .andExpect(content().string(containsString("STATUS_GROUP")))
            .andExpect(content().string(containsString("/system/fields/1")));
    }

    @Test void formOffersOptionGroups() throws Exception {
        when(service.options()).thenReturn(List.of(option(1L, "STATUS_GROUP")));
        mvc.perform(get("/system/fields/new").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"))
            .andExpect(content().string(containsString("STATUS_GROUP")))
            .andExpect(content().string(containsString("name=\"optionIdx\"")));
    }

    @Test void blankTableShowsFormAgain() throws Exception {
        when(service.options()).thenReturn(List.of());
        when(service.optionExists(any())).thenReturn(true);
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "").param("fieldName", "STATUS").param("fieldType", "select")
                .param("pk", "0").param("fk", "0").param("editable", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"));
    }

    @Test void negativeFlagShowsFormAgain() throws Exception {
        when(service.options()).thenReturn(List.of());
        when(service.optionExists(any())).thenReturn(true);
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "APPID").param("fieldName", "STATUS").param("fieldType", "select")
                .param("pk", "-1").param("fk", "0").param("editable", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.optionExists(any())).thenReturn(true);
        when(service.create(any())).thenReturn(field(9L, null));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "APPID").param("fieldName", "MEMO").param("fieldType", "text")
                .param("pk", "0").param("fk", "0").param("editable", "1").param("optionIdx", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/fields/9"));
    }

    /** 등록 시 존재하지 않는 코드 그룹(예: 999)을 지정하면 저장 전에 막혀 폼을 다시 그린다. */
    @Test void createWithUnknownOptionShowsFormAgain() throws Exception {
        when(service.optionExists(999L)).thenReturn(false);
        when(service.options()).thenReturn(List.of());
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "APPID").param("fieldName", "STATUS").param("fieldType", "select")
                .param("pk", "0").param("fk", "0").param("editable", "0").param("optionIdx", "999"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"))
            .andExpect(content().string(containsString("존재하지 않는 코드 그룹입니다.")));
        verify(service, never()).create(any());
    }

    @Test void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/system/fields").with(user(superUser)).param("fieldTable", "x")).andExpect(status().isForbidden());
    }
}
