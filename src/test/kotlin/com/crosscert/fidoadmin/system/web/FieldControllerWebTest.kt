package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.system.entity.CcfaFields
import com.crosscert.fidoadmin.system.entity.CcfaOption
import com.crosscert.fidoadmin.system.service.FieldService
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Sort
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@WebMvcTest(controllers = [FieldController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class FieldControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: FieldService
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun field(idx: Long, optionIdx: Long?): CcfaFields {
        val f = CcfaFields(); f.idx = idx; f.fieldTable = "APPID"; f.fieldName = "STATUS"; f.fieldType = "select"
        f.fieldTitle = "상태"; f.pk = 0L; f.fk = 0L; f.editable = 1L; f.optionIdx = optionIdx
        return f
    }
    private fun option(idx: Long, name: String): CcfaOption { val o = CcfaOption(); o.idx = idx; o.optionName = name; return o }

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/system/fields").with(user(companyUser))).andExpect(status().isForbidden())
    }

    /** 목록은 OPTION_IDX 대신 코드 그룹 이름을 보여준다. */
    @Test fun listRendersRowsWithOptionName() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.options()).thenReturn(listOf(option(1L, "STATUS_GROUP")))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf(field(1L, 1L))))
        mvc.perform(get("/system/fields").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/list"))
            .andExpect(content().string(containsString("APPID")))
            .andExpect(content().string(containsString("STATUS_GROUP")))
            .andExpect(content().string(containsString("/system/fields/1")))
    }

    @Test fun formOffersOptionGroups() {
        whenever(service.options()).thenReturn(listOf(option(1L, "STATUS_GROUP")))
        mvc.perform(get("/system/fields/new").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"))
            .andExpect(content().string(containsString("STATUS_GROUP")))
            .andExpect(content().string(containsString("name=\"optionIdx\"")))
    }

    @Test fun blankTableShowsFormAgain() {
        whenever(service.options()).thenReturn(listOf())
        whenever(service.optionExists(anyOrNull())).thenReturn(true)
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "").param("fieldName", "STATUS").param("fieldType", "select")
                .param("pk", "0").param("fk", "0").param("editable", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"))
    }

    @Test fun negativeFlagShowsFormAgain() {
        whenever(service.options()).thenReturn(listOf())
        whenever(service.optionExists(anyOrNull())).thenReturn(true)
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "APPID").param("fieldName", "STATUS").param("fieldType", "select")
                .param("pk", "-1").param("fk", "0").param("editable", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"))
    }

    @Test fun createRedirectsToDetail() {
        whenever(service.optionExists(anyOrNull())).thenReturn(true)
        whenever(service.create(any())).thenReturn(field(9L, null))
        whenever(service.idOf(any())).thenReturn("9")
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "APPID").param("fieldName", "MEMO").param("fieldType", "text")
                .param("pk", "0").param("fk", "0").param("editable", "1").param("optionIdx", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/fields/9"))
    }

    /** 등록 시 존재하지 않는 코드 그룹(예: 999)을 지정하면 저장 전에 막혀 폼을 다시 그린다. */
    @Test fun createWithUnknownOptionShowsFormAgain() {
        whenever(service.optionExists(999L)).thenReturn(false)
        whenever(service.options()).thenReturn(listOf())
        mvc.perform(post("/system/fields").with(user(superUser)).with(csrf())
                .param("fieldTable", "APPID").param("fieldName", "STATUS").param("fieldType", "select")
                .param("pk", "0").param("fk", "0").param("editable", "0").param("optionIdx", "999"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/form"))
            .andExpect(content().string(containsString("존재하지 않는 코드 그룹입니다.")))
        verify(service, never()).create(any())
    }

    @Test fun postWithoutCsrfIsForbidden() {
        mvc.perform(post("/system/fields").with(user(superUser)).param("fieldTable", "x")).andExpect(status().isForbidden())
    }

    /** 상세는 코드 그룹 이름과 IDX 를 함께 보여준다(특성화). */
    @Test fun detailShowsOptionNameAndIdx() {
        whenever(service.get(1L)).thenReturn(field(1L, 1L))
        whenever(service.option(1L)).thenReturn(option(1L, "STATUS_GROUP"))
        whenever(service.option(2L)).thenReturn(option(2L, "OTHER_GROUP"))
        mvc.perform(get("/system/fields/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/fields/detail"))
            .andExpect(content().string(containsString("<th>코드 그룹</th><td>STATUS_GROUP (1)</td>")))
            .andExpect(content().string(not(containsString("OTHER_GROUP"))))
        verify(service, never()).options()
    }

    /** 코드 그룹이 없으면(optionIdx == null) 칸이 비어 있다(특성화). */
    @Test fun detailWithoutOptionShowsEmptyCell() {
        whenever(service.get(1L)).thenReturn(field(1L, null))
        whenever(service.option(1L)).thenReturn(option(1L, "STATUS_GROUP"))
        mvc.perform(get("/system/fields/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<th>코드 그룹</th><td></td>")))
            .andExpect(content().string(not(containsString("STATUS_GROUP"))))
        verify(service, never()).option(any())
    }

    /** 가리키는 코드 그룹 행이 없으면 이름 없이 IDX 만 보인다(특성화). */
    @Test fun detailWithMissingOptionShowsIdxOnly() {
        whenever(service.get(1L)).thenReturn(field(1L, 999L))
        whenever(service.option(1L)).thenReturn(option(1L, "STATUS_GROUP"))
        whenever(service.option(999L)).thenReturn(null)
        mvc.perform(get("/system/fields/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<th>코드 그룹</th><td> (999)</td>")))
            .andExpect(content().string(not(containsString("STATUS_GROUP"))))
    }

    /** 코드 그룹 행은 있지만 이름이 null 이면 IDX 만 보인다(특성화). */
    @Test fun detailWithNullOptionNameShowsIdxOnly() {
        whenever(service.get(1L)).thenReturn(field(1L, 1L))
        val nameless = CcfaOption(); nameless.idx = 1L
        whenever(service.option(1L)).thenReturn(nameless)
        mvc.perform(get("/system/fields/1").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<th>코드 그룹</th><td> (1)</td>")))
    }
}
