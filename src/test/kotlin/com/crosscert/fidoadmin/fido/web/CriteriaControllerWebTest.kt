package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
import com.crosscert.fidoadmin.auth.LoginFailureHandler
import com.crosscert.fidoadmin.auth.LoginSuccessHandler
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.auth.ManagerUserDetailsService
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.fido.entity.Criteria
import com.crosscert.fidoadmin.fido.service.CriteriaMetadataException
import com.crosscert.fidoadmin.fido.service.CriteriaMetadataParser
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@WebMvcTest(controllers = [CriteriaController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    TenantContext::class, SelectedTenant::class)
class CriteriaControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: SelectedTenant
    @MockitoBean lateinit var service: CriteriaQueryService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    /**
     * /criteria 는 TENANT 영역이라 미선택 SUPER 는 TenantSelectionInterceptor 가
     * /select-tenant 로 돌려보낸다. SUPER 요청에는 미리 테넌트를 선택해 둔다.
     */
    lateinit var session: MockHttpSession

    @BeforeEach fun selectTenant() {
        session = MockHttpSession()
        val request = MockHttpServletRequest()
        request.setSession(session)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            selected.select(9L)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
    }

    private fun criteria(): Criteria {
        val c = Criteria()
        c.idx = 1L; c.aaid = "0012#0001"; c.vendorids = "0012"; c.userverification = 2L; c.keyprotection = 2L
        c.matcherprotection = 2L; c.attachmenthnumber = 1L; c.tcdisplay = 1L; c.tcdisplaycontenttype = "text/plain"
        c.authenticationalgorithms = "1"; c.assertionschemes = "UAFV1TLV"; c.attestationtypes = "15879"
        c.authenticatorversion = 1L; c.metahash = "hash-0001"
        c.jsondata = "{\"aaid\":\"0012#0001\",\"description\":\"Sample fingerprint\"}"
        c.createtime = LocalDateTime.of(2026, 9, 1, 9, 0); c.updatedtime = LocalDateTime.of(2026, 9, 17, 10, 0)
        return c
    }

    /**
     * 화면의 용도가 고객사별 AAID 토글이라 COMPANY 역할도 연다.
     * 예전에는 SUPER 전용이라 403 이었다 — 그 제약이 풀렸음을 못 박는다.
     */
    @Test fun companyRoleCanView() {
        whenever(service.disabledAaids()).thenReturn(java.util.Set.of())
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf(criteria())))
        whenever(service.get(1L)).thenReturn(criteria())

        mvc.perform(get("/criteria").with(user(companyUser))).andExpect(status().isOk())
        mvc.perform(get("/criteria/1").with(user(companyUser))).andExpect(status().isOk())
    }

    @Test fun superSeesListWithoutJsonAndWithoutCompanyFilter() {
        whenever(service.disabledAaids()).thenReturn(java.util.Set.of())
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf(criteria())))
        mvc.perform(get("/criteria").param("aaid", "0012").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/list"))
            .andExpect(content().string(containsString("0012#0001")))
            .andExpect(content().string(containsString("hash-0001")))
            .andExpect(content().string(not(containsString("Sample fingerprint"))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(containsString("/criteria/new")))
            // 이 화면에는 삭제가 없다. 상태 토글 확인은 README 규칙대로 네이티브 confirm() 이 아니라 모달이다.
            .andExpect(content().string(not(containsString("/delete"))))
            .andExpect(content().string(not(containsString("confirm("))))
            .andExpect(content().string(containsString("data-confirm-form=\"aaidBulkOnForm\"")))
            .andExpect(content().string(containsString("data-confirm-form=\"aaidBulkOffForm\"")))
            .andExpect(content().string(containsString("data-confirm-form=\"aaidForm1\"")))
        val captor = ArgumentCaptor.forClass(CriteriaSearchForm::class.java)
        verify(service).search(captor.capture(), any<Pageable>())
        assertThat(captor.value.aaid).isEqualTo("0012")
    }

    /** 상세의 JSONDATA 는 JsonPretty 로 정리되어 "key" : "value" 형태(이스케이프된 &quot;)로 나온다. */
    @Test fun detailShowsPrettyJson() {
        whenever(service.get(1L)).thenReturn(criteria())
        mvc.perform(get("/criteria/1").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/detail"))
            .andExpect(content().string(containsString("&quot;aaid&quot; : &quot;0012#0001&quot;")))
            .andExpect(content().string(containsString("UAFV1TLV")))
            .andExpect(content().string(not(containsString("data-confirm-form"))))
    }

    /** 전체 토글 후 검색·페이지 상태를 유지한 채 목록으로 돌아간다. */
    @Test fun bulkStatusRedirectsBackWithSearchStateAndMessage() {
        whenever(service.changeStatusAll(false)).thenReturn(4)

        mvc.perform(post("/criteria/status").with(user(companyUser)).with(csrf())
                .param("enabled", "false").param("aaid", "0012").param("page", "1").param("size", "20"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/criteria?aaid=0012&page=1&size=20"))
            .andExpect(flash().attribute("statusMessage", "전체 비활성: 4건을 변경했습니다."))

        verify(service).changeStatusAll(false)
    }

    @Test fun companyDoesNotSeeCreateButton() {
        whenever(service.disabledAaids()).thenReturn(java.util.Set.of())
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf(criteria())))
        mvc.perform(get("/criteria").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/criteria/new"))))
    }

    @Test fun superOpensCreateForm() {
        mvc.perform(get("/criteria/new").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/form"))
            .andExpect(content().string(containsString("name=\"jsondata\"")))
    }

    @Test fun companyCannotOpenCreateForm() {
        mvc.perform(get("/criteria/new").with(user(companyUser)))
            .andExpect(status().isForbidden())
    }

    @Test fun companyCannotPostCreate() {
        mvc.perform(post("/criteria").with(user(companyUser)).with(csrf())
                .param("jsondata", "{\"aaid\":\"0012#0009\"}"))
            .andExpect(status().isForbidden())
        verify(service, never()).create(anyString())
    }

    @Test fun superCreateRedirectsToDetail() {
        val saved = criteria()
        saved.idx = 30L
        whenever(service.create("{\"aaid\":\"0012#0009\"}")).thenReturn(saved)
        mvc.perform(post("/criteria").session(session).with(user(superUser)).with(csrf())
                .param("jsondata", "{\"aaid\":\"0012#0009\"}"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/criteria/30"))
            .andExpect(flash().attribute("flashSuccess", "등록되었습니다. 모든 고객사에서 비활성 상태로 시작합니다."))
    }

    @Test fun parseErrorShowsFormWithMessageAndKeepsInput() {
        whenever(service.create("{bad")).thenThrow(CriteriaMetadataException(CriteriaMetadataParser.INVALID))
        mvc.perform(post("/criteria").session(session).with(user(superUser)).with(csrf())
                .param("jsondata", "{bad"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/form"))
            .andExpect(content().string(containsString(CriteriaMetadataParser.INVALID)))
            .andExpect(content().string(containsString("{bad")))
    }

    @Test fun blankJsonShowsFormAgain() {
        mvc.perform(post("/criteria").session(session).with(user(superUser)).with(csrf())
                .param("jsondata", "   "))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/criteria/form"))
        verify(service, never()).create(anyString())
    }
}
