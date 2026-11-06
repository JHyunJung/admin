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
import com.crosscert.fidoadmin.fido.entity.Challenge
import com.crosscert.fidoadmin.fido.service.ChallengeQueryService
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@WebMvcTest(controllers = [ChallengeController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class)
class ChallengeControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: SelectedTenant
    @MockitoBean lateinit var service: ChallengeQueryService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun challenge(): Challenge {
        val c = Challenge()
        c.idx = 7L; c.companyIdx = 1L; c.userid = "user001"; c.servicename = "kbstar"
        c.challengecode = "chal-0001"; c.createtime = LocalDateTime.of(2026, 9, 17, 10, 0); c.logidx = -1L; c.uv = "fingerprint"
        return c
    }

    /**
     * TenantSelectionInterceptor 가 미선택 SUPER 를 /select-tenant 로 돌려보낸다.
     * 이 클래스가 보는 경로는 TENANT 영역이므로, SUPER 요청에는 미리 테넌트를 선택해 둔다.
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


    /** 목록에서 검색 select 뿐 아니라 고객사 컬럼도 뺐다(세션이 테넌트를 정한다). */
    @Test fun companyUserSeesListWithoutCompanyFilterAndWithoutWriteButtons() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf(challenge())))
        whenever(companies.names()).thenReturn(mapOf(1L to "KB국민은행"))
        mvc.perform(get("/challenges").param("userid", "user001").param("servicename", "kbstar").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/challenge/list"))
            .andExpect(content().string(containsString("chal-0001")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
            .andExpect(content().string(not(containsString("/challenges/new"))))
            .andExpect(content().string(not(containsString("data-confirm-form"))))
        val captor = ArgumentCaptor.forClass(ChallengeSearchForm::class.java)
        verify(service).search(captor.capture(), any<Pageable>())
        assertThat(captor.value.userid).isEqualTo("user001")
        assertThat(captor.value.servicename).isEqualTo("kbstar")
    }

    /**
     * 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 는 다른 폼의 hidden 필드에도 쓰일 수 있으므로 검색폼 select 태그로 특정한다.
     */
    @Test fun superUserDoesNotSeeCompanyFilterOnList() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf()))
        mvc.perform(get("/challenges").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))))
            .andExpect(content().string(containsString("데이터가 없습니다.")))
    }

    /** 빈 목록은 일러스트와 문구를 보인다. 빈 행에는 링크가 없어 행 클릭 대상이 아니다. */
    /** 첫 페이지의 "이전" 은 링크가 아니라 비활성 표시다(page=-1 로 가는 href 를 내지 않는다). */
    @Test fun 첫_페이지의_이전_링크는_href_없이_비활성이다() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(
            PageImpl(listOf(challenge()), PageRequest.of(0, 1), 3))
        whenever(companies.names()).thenReturn(mapOf(1L to "KB국민은행"))
        val html = mvc.perform(get("/challenges").with(user(companyUser)))
            .andExpect(status().isOk()).andReturn().response.contentAsString
        val prev = html.substring(html.indexOf("aria-label=\"이전 페이지\"") - 40, html.indexOf("aria-label=\"이전 페이지\"") + 80)
        assertThat(prev).contains("aria-disabled=\"true\"").doesNotContain("href=").doesNotContain("page=-1")
        val next = html.substring(html.indexOf("aria-label=\"다음 페이지\"") - 40, html.indexOf("aria-label=\"다음 페이지\"") + 80)
        assertThat(next).contains("href=").contains("page=1").doesNotContain("aria-disabled")
    }

    @Test fun 빈_목록은_일러스트와_문구를_보인다() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf()))
        val html = mvc.perform(get("/challenges").session(session).with(user(superUser)))
            .andExpect(status().isOk()).andReturn().response.contentAsString
        assertThat(html).contains("/img/ill/empty", ".webp").contains("데이터가 없습니다.")
        var empty = html.substring(html.indexOf("fa-empty-cell"))
        empty = empty.substring(0, empty.indexOf("</tr>"))
        assertThat(empty).doesNotContain("href=")
    }

    @Test fun detailRendersAllColumns() {
        whenever(service.get(7L)).thenReturn(challenge())
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/challenges/7").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/challenge/detail"))
            .andExpect(content().string(containsString("chal-0001")))
            .andExpect(content().string(containsString("fingerprint")))
            .andExpect(content().string(containsString("KB국민은행")))
            .andExpect(content().string(not(containsString("data-confirm-form"))))
    }
}
