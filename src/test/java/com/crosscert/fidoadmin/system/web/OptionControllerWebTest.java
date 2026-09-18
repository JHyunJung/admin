package com.crosscert.fidoadmin.system.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.entity.CcfaOptions;
import com.crosscert.fidoadmin.system.service.OptionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OptionController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class,
    com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class OptionControllerWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean OptionService service;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private CcfaOption group(long idx, String name) { CcfaOption g = new CcfaOption(); g.setIdx(idx); g.setOptionName(name); g.setOptionTitle("상태"); return g; }
    private CcfaOptions item(long idx, String value, String title) {
        CcfaOptions i = new CcfaOptions(); i.setIdx(idx); i.setOptionIdx(1L); i.setOptionValue(value); i.setOptionTitle(title); return i;
    }

    @Test void companyRoleIsForbidden() throws Exception {
        mvc.perform(get("/system/options").with(user(companyUser))).andExpect(status().isForbidden());
        mvc.perform(post("/system/options/1/items").with(user(companyUser)).with(csrf()).param("optionValue", "x"))
            .andExpect(status().isForbidden());
    }

    @Test void listRendersRows() throws Exception {
        when(service.defaultSort()).thenReturn(Sort.by("idx"));
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(group(1L, "STATUS"))));
        mvc.perform(get("/system/options").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/options/list"))
            .andExpect(content().string(containsString("STATUS")))
            .andExpect(content().string(containsString("/system/options/1")));
    }

    /** 상세는 그룹 정보 + 코드 목록 + 인라인 추가 폼 + 코드별 삭제 폼을 함께 그린다. */
    @Test void detailListsItemsWithInlineForms() throws Exception {
        when(service.get(1L)).thenReturn(group(1L, "STATUS"));
        when(service.items(1L)).thenReturn(List.of(item(11L, "use", "사용"), item(12L, "unuse", "미사용")));
        mvc.perform(get("/system/options/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/options/detail"))
            .andExpect(content().string(containsString("사용")))
            .andExpect(content().string(containsString("미사용")))
            .andExpect(content().string(containsString("action=\"/system/options/1/items\"")))
            .andExpect(content().string(containsString("action=\"/system/options/1/items/12/delete\"")))
            .andExpect(content().string(containsString("data-confirm-form=\"delItem12\"")));
    }

    @Test void addItemRedirectsToDetailAndCallsService() throws Exception {
        when(service.addItem(eq(1L), any())).thenReturn(item(13L, "hold", "보류"));
        mvc.perform(post("/system/options/1/items").with(user(superUser)).with(csrf())
                .param("optionValue", "hold").param("optionTitle", "보류").param("optionNote", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/1"))
            .andExpect(flash().attribute("flashSuccess", "코드가 추가되었습니다."));
        ArgumentCaptor<CcfaOptions> captor = ArgumentCaptor.forClass(CcfaOptions.class);
        verify(service).addItem(eq(1L), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getOptionValue()).isEqualTo("hold");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getOptionTitle()).isEqualTo("보류");
    }

    /** 코드 값이 비면 저장하지 않고 상세로 돌아가 오류 플래시를 보인다. */
    @Test void addItemWithBlankValueRedirectsWithError() throws Exception {
        mvc.perform(post("/system/options/1/items").with(user(superUser)).with(csrf()).param("optionValue", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/1"))
            .andExpect(flash().attribute("flashError", "코드 값은 필수입니다(128바이트 이내)."));
        verify(service, never()).addItem(any(), any());
    }

    @Test void removeItemRedirectsToDetail() throws Exception {
        mvc.perform(post("/system/options/1/items/12/delete").with(user(superUser)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/1"))
            .andExpect(flash().attribute("flashSuccess", "코드가 삭제되었습니다."));
        verify(service).removeItem(1L, 12L);
    }

    @Test void blankGroupNameShowsFormAgain() throws Exception {
        mvc.perform(post("/system/options").with(user(superUser)).with(csrf()).param("optionName", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("system/options/form"));
    }

    @Test void createRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(group(9L, "NEW"));
        when(service.idOf(any())).thenReturn("9");
        mvc.perform(post("/system/options").with(user(superUser)).with(csrf()).param("optionName", "NEW").param("optionTitle", "신규"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/options/9"));
    }
}
