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
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.fido.entity.Appid
import com.crosscert.fidoadmin.fido.service.AppidService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@WebMvcTest(controllers = [AppidController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class)
class AppidControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: SelectedTenant
    @MockitoBean lateinit var service: AppidService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun appid(idx: Long): Appid {
        val a = Appid(); a.idx = idx; a.companyIdx = 1L; a.appid = "https://kbstar.com/facets.json"
        a.servicename = "kbstar"; a.status = "use"; a.device = "android"; a.deviceDefault = "T"
        return a
    }

    private fun kb(): CcfaCompany { val c = CcfaCompany(); c.idx = 1L; c.companyName = "KB국민은행"; return c }

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
    @Test fun companyUserSeesListWithoutCompanyFilter() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf(appid(1L))))
        whenever(companies.names()).thenReturn(mapOf(1L to "KB국민은행"))
        mvc.perform(get("/appids").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appid/list"))
            .andExpect(content().string(containsString("https://kbstar.com/facets.json")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
    }

    /** "AppID 관리" 목록 규격 — 장치/기본값/상태를 코드가 아니라 표시어로 보여 준다. */
    @Test fun listRendersColumnsAndLabels() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf(appid(1L))))
        whenever(companies.names()).thenReturn(mapOf(1L to "KB국민은행"))
        val html = mvc.perform(get("/appids").with(user(companyUser)))
            .andExpect(status().isOk())
            .andReturn().response.contentAsString
        assertThat(html)
            .contains("AppID 관리")
            .contains("추가하기")
            .contains(">AOS<")              // device=android
            .contains("bi-android2")         // AOS 앞 안드로이드 로고
            .contains(">설정<")             // deviceDefault=T
            .contains(">활성<")             // status=use
            .contains("1건이 검색되었습니다.")
            .doesNotContain(">use<")
        // 컬럼 순서: 번호 · APPID · 서비스명 · 장치 · 설명 · 기본값 · 상태 · 생성일 · 수정일 (날짜는 항상 맨 끝)
        val pos = intArrayOf(html.indexOf("<th class=\"text-end\">번호</th>"), html.indexOf("<th>APPID</th>"), html.indexOf("<th>서비스명</th>"),
            html.indexOf("<th>장치</th>"), html.indexOf("<th>설명</th>"), html.indexOf("<th>기본값</th>"),
            html.indexOf("<th>상태</th>"), html.indexOf("<th>생성일</th>"), html.indexOf("<th>수정일</th>"))
        for (i in pos.indices) assertThat(pos[i]).`as`("column $i").isGreaterThan(if (i == 0) -1 else pos[i - 1])
    }

    /**
     * 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 는 다른 폼의 hidden 필드에도 쓰일 수 있으므로 검색폼 select 태그로 특정한다.
     */
    @Test fun superUserDoesNotSeeCompanyFilterOnList() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf()))
        whenever(companies.all()).thenReturn(listOf(kb()))
        mvc.perform(get("/appids").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))))
    }

    @Test fun blankAppidShowsFormAgain() {
        mvc.perform(post("/appids").with(user(companyUser)).with(csrf())
                .param("appid", "").param("status", "use").param("deviceDefault", "F"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appid/form"))
    }

    @Test fun createRedirectsToDetail() {
        val saved = appid(5L)
        whenever(service.create(any())).thenReturn(saved)
        whenever(service.idOf(any())).thenReturn("5")
        mvc.perform(post("/appids").with(user(companyUser)).with(csrf())
                .param("appid", "https://kbstar.com/facets.json").param("status", "use").param("deviceDefault", "F"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/appids/5"))
    }

    @Test fun detailRendersAllColumns() {
        whenever(service.get(3L)).thenReturn(appid(3L))
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/appids/3").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appid/detail"))
            .andExpect(content().string(containsString("kbstar")))
            .andExpect(content().string(containsString("KB국민은행")))
    }

    /** 운영 문의: "기본기기" 가 무슨 뜻인지 모르겠다 — appid/설명/장치/서비스명/기본값/상태 순서와 표시어로 받는다. */
    @Test fun newFormUsesFieldOrderAndLabels() {
        val html = mvc.perform(get("/appids/new").with(user(companyUser)))
            .andExpect(status().isOk())
            .andReturn().response.contentAsString
        assertThat(html)
            .doesNotContain("기본기기")
            .contains("value=\"ios\"").contains(">iOS<")
            .contains("value=\"android\"").contains(">AOS<")
            .contains("value=\"T\"").contains(">설정<")
            .contains("value=\"F\"").contains(">설정안함<")
            .contains("value=\"use\"").contains(">활성<")
            .contains("value=\"unuse\"").contains(">비활성<")
        val pos = intArrayOf(html.indexOf(">APPID<span"), html.indexOf(">설명<"), html.indexOf(">장치<"),
            html.indexOf(">서비스명<"), html.indexOf(">기본값<span"), html.indexOf(">상태<span"))
        for (i in pos.indices) assertThat(pos[i]).`as`("field $i").isGreaterThan(if (i == 0) -1 else pos[i - 1])
    }

    /** 표에 없는 옛 장치 값으로 수정 화면을 열어도 그 값이 선택지에 남아 저장 때 지워지지 않는다. */
    @Test fun editFormKeepsUnknownDeviceValue() {
        val a = appid(4L); a.device = "windows"
        whenever(service.get(4L)).thenReturn(a)
        whenever(service.idOf(any())).thenReturn("4")
        mvc.perform(get("/appids/4/edit").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("value=\"windows\" selected")))
    }

    @Test fun detailUsesDisplayLabels() {
        whenever(service.get(3L)).thenReturn(appid(3L))
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        val html = mvc.perform(get("/appids/3").with(user(companyUser)))
            .andExpect(status().isOk())
            .andReturn().response.contentAsString
        assertThat(html)
            .doesNotContain("기본기기").doesNotContain(">use<")
            .contains("<th>설명</th>").contains("<th>장치</th>").contains("<th>기본값</th>")
            .contains(">AOS<").contains(">설정<").contains(">활성<")
    }

    /** V2 브레드크럼 3단: 목록 아래 화면은 "그룹 / 메뉴 / 등록·수정·상세" 로 그린다(목록은 2단). */
    @Test fun breadcrumbHasThirdSegmentBelowList() {
        whenever(service.get(3L)).thenReturn(appid(3L))
        whenever(service.get(4L)).thenReturn(appid(4L))
        whenever(service.idOf(any())).thenReturn("4")
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        assertThat(crumbsOf("/appids/new"))
            .contains("FIDO 서버 관리").contains("AppID 관리").contains("등록").doesNotContain("상세").doesNotContain("수정")
        assertThat(crumbsOf("/appids/3"))
            .contains("FIDO 서버 관리").contains("AppID 관리").contains("상세").doesNotContain("등록").doesNotContain("수정")
        assertThat(crumbsOf("/appids/4/edit"))
            .contains("FIDO 서버 관리").contains("AppID 관리").contains("수정").doesNotContain("상세").doesNotContain("등록")
    }

    private fun crumbsOf(path: String): String {
        val html = mvc.perform(get(path).with(user(companyUser)))
            .andExpect(status().isOk()).andReturn().response.contentAsString
        val crumbs = html.substring(html.indexOf("aria-label=\"현재 위치\""))
        return crumbs.substring(0, crumbs.indexOf("</nav>"))
    }

    @Test fun detailShowsAppleLogoForIos() {
        val a = appid(6L); a.device = "ios"
        whenever(service.get(6L)).thenReturn(a)
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/appids/6").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("bi-apple")))
            .andExpect(content().string(not(containsString("bi-android2"))))
    }

    @Test fun postWithoutCsrfIsForbidden() {
        mvc.perform(post("/appids").with(user(companyUser)).param("appid", "x")).andExpect(status().isForbidden())
    }
}
