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
import com.crosscert.fidoadmin.fido.entity.Userinfo
import com.crosscert.fidoadmin.fido.service.UserAccountRow
import com.crosscert.fidoadmin.fido.service.UserAccountService
import com.crosscert.fidoadmin.fido.service.UserinfoService
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
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

@WebMvcTest(controllers = [UserController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class)
class UserControllerWebTest {

    companion object {
        const val FULL_PUBKEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE-PUBKEY-FULL"
        const val FULL_CERT = "MIIB-CERT-SAMPLE-FULL-VALUE-DO-NOT-SHOW"

        private fun countOf(haystack: String, needle: String): Int {
            var count = 0
            var i = haystack.indexOf(needle)
            while (i >= 0) { count++; i = haystack.indexOf(needle, i + needle.length) }
            return count
        }
    }

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: SelectedTenant
    @MockitoBean lateinit var service: UserinfoService
    @MockitoBean lateinit var accounts: UserAccountService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun accountRow(userid: String): UserAccountRow =
        UserAccountRow(userid, "kbstar", 3L, 2L, LocalDateTime.now())

    private fun userinfo(idx: Long): Userinfo {
        val u = Userinfo(); u.idx = idx; u.companyIdx = 1L; u.servicename = "kbstar"; u.userid = "user001"
        u.aaid = "0012#0001"; u.status = "O"; u.signcounter = 12L; u.keyid = "keyid-001"
        u.pubkey = FULL_PUBKEY; u.certificate = FULL_CERT; u.regtime = LocalDateTime.of(2026, 9, 1, 10, 0)
        return u
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

    // Java 원본의 any() 는 null 도 받는다. 검색어 없이 부르면 userid·servicename 이 null 이라 anyOrNull() 로 옮긴다.

    /**
     * 사용자 단위 목록에는 민감 컬럼이 실리지 않는다. 묶음 행(UserAccountRow)에는
     * 애초에 PUBKEY·CERTIFICATE 필드가 없지만, 나중에 누가 행에 필드를 더할 수 있어
     * 화면 출력으로 고정해 둔다.
     */
    @Test fun listNeverExposesPubkeyOrCertificate() {
        whenever(accounts.search(anyOrNull(), anyOrNull(), any<Pageable>()))
            .thenReturn(PageImpl(listOf(accountRow("user001"))))
        mvc.perform(get("/users").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/user/list"))
            .andExpect(content().string(containsString("user001")))
            .andExpect(content().string(not(containsString(FULL_PUBKEY))))
            .andExpect(content().string(not(containsString("MFkwEwYHKoZIzj0C"))))
            .andExpect(content().string(not(containsString(FULL_CERT))))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
    }

    /**
     * back 은 "목록" 링크의 href 로 그대로 나간다. th:href 는 HTML 이스케이프만 하고 스킴은 검사하지
     * 않으므로, 검증 없이 넘기면 javascript: 링크나 외부 주소가 운영자 화면에 박힌다.
     * 기기 목록 경로(/users/{userid}/credentials...)만 받고 그 밖은 기본 목록으로 돌린다.
     */
    @Test fun detailDropsBackThatIsNotACredentialsPath() {
        whenever(service.get(1L)).thenReturn(userinfo(1L))
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        for (evil in listOf("javascript:alert(1)", "https://evil.example/", "//evil.example/x", "/system/props")) {
            mvc.perform(get("/users/1").param("back", evil).with(user(companyUser)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(evil))))
                .andExpect(content().string(containsString("href=\"/users\"")))
        }
    }

    @Test fun detailKeepsBackPointingToCredentialsList() {
        whenever(service.get(1L)).thenReturn(userinfo(1L))
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        val back = "/users/user001/credentials?servicename=kbstar"
        mvc.perform(get("/users/1").param("back", back).with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/users/user001/credentials?servicename=kbstar\"")))
    }

    @Test fun detailShowsOnlyFirst16CharsOfSensitiveColumns() {
        whenever(service.get(1L)).thenReturn(userinfo(1L))
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/users/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/user/detail"))
            .andExpect(content().string(containsString("MFkwEwYHKoZIzj0C")))
            .andExpect(content().string(not(containsString(FULL_PUBKEY))))
            .andExpect(content().string(containsString("MIIB-CERT-SAMPLE")))
            .andExpect(content().string(not(containsString(FULL_CERT))))
            .andExpect(content().string(containsString("/users/1/status")))
    }

    /** 상세 화면은 항목을 구획 카드로 나눠 보여준다. 모든 항목은 그대로 남는다. */
    @Test fun detailSplitsRowsIntoTitledSections() {
        whenever(service.get(1L)).thenReturn(userinfo(1L))
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/users/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("fa-section-title")))
            .andExpect(content().string(containsString("기본 정보")))
            .andExpect(content().string(containsString("인증기기 정보")))
            .andExpect(content().string(containsString("상태 · 이력")))
            // 구획을 나눠도 항목은 하나도 사라지지 않는다
            .andExpect(content().string(containsString("USERID")))
            .andExpect(content().string(containsString("AAID")))
            .andExpect(content().string(containsString("KEYID")))
            .andExpect(content().string(containsString("서명횟수")))
            .andExpect(content().string(containsString("등록일시")))
            .andExpect(content().string(containsString("생성일시")))
    }

    /**
     * 구획 프래그먼트 정의가 본문에 그대로 한 번 더 찍히면 안 된다.
     * (th:fragment 를 단 tbody 를 main 안에 두면 카드 밖에 평문으로 중복 출력된다.)
     */
    @Test fun detailRendersEachRowExactlyOnce() {
        whenever(service.get(1L)).thenReturn(userinfo(1L))
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        val html = mvc.perform(get("/users/1").with(user(companyUser)))
            .andExpect(status().isOk())
            .andReturn().response.contentAsString
        assertThat(countOf(html, "<th>USERID</th>")).isEqualTo(1)
        assertThat(countOf(html, "<th>AAID</th>")).isEqualTo(1)
        assertThat(countOf(html, "user001")).isEqualTo(1)
        assertThat(countOf(html, "0012#0001")).isEqualTo(1)
    }

    @Test fun detailHasNoEditOrDeleteLinks() {
        whenever(service.get(1L)).thenReturn(userinfo(1L))
        mvc.perform(get("/users/1").with(user(companyUser)))
            .andExpect(content().string(not(containsString("/users/1/edit"))))
            .andExpect(content().string(not(containsString("/users/1/delete"))))
    }

    @Test fun statusChangeRedirectsWithFlash() {
        whenever(service.changeStatus(1L, "X")).thenReturn(userinfo(1L))
        mvc.perform(post("/users/1/status").with(user(companyUser)).with(csrf()).param("status", "X"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/users/1"))
            .andExpect(flash().attribute("flashSuccess", "상태가 변경되었습니다."))
        verify(service).changeStatus(eq(1L), eq("X"))
    }

    @Test fun invalidStatusRedirectsWithError() {
        whenever(service.changeStatus(1L, "Z")).thenThrow(IllegalArgumentException("허용되지 않는 상태입니다: Z"))
        mvc.perform(post("/users/1/status").with(user(companyUser)).with(csrf()).param("status", "Z"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/users/1"))
            .andExpect(flash().attribute("flashError", "허용되지 않는 상태입니다: Z"))
    }

    @Test fun statusChangeWithoutCsrfIsForbidden() {
        mvc.perform(post("/users/1/status").with(user(companyUser)).param("status", "X")).andExpect(status().isForbidden())
    }

    /**
     * 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 는 다른 폼의 hidden 필드에도 쓰일 수 있으므로 검색폼 select 태그로 특정한다.
     */
    /** 고객사는 세션이 정한다. 목록에 고객사 선택 필터가 있으면 안 된다. */
    @Test fun superUserDoesNotSeeCompanyFilterOnList() {
        whenever(accounts.search(anyOrNull(), anyOrNull(), any<Pageable>())).thenReturn(PageImpl(listOf()))
        mvc.perform(get("/users").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))))
    }

    /** 목록은 사용자 단위다 — 기기 수와 정상 수가 보여야 "몇 대 등록했나"에 답할 수 있다. */
    @Test fun 목록은_사용자_단위로_집계를_보여준다() {
        whenever(accounts.search(anyOrNull(), anyOrNull(), any<Pageable>()))
            .thenReturn(PageImpl(listOf(accountRow("user001"))))

        mvc.perform(get("/users").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/user/list"))
            .andExpect(content().string(containsString("user001")))
            .andExpect(content().string(containsString("기기 수")))
            .andExpect(content().string(containsString("/users/user001/credentials")))
    }

    /** 검색어가 서비스까지 전달되어야 한다. 화면만 있고 조건이 안 걸리면 조용히 전체가 나온다. */
    @Test fun 검색어가_서비스로_전달된다() {
        whenever(accounts.search(anyOrNull(), anyOrNull(), any<Pageable>())).thenReturn(PageImpl(listOf()))

        mvc.perform(get("/users").session(session).with(user(superUser))
                .param("userid", "user0").param("servicename", "kb"))
            .andExpect(status().isOk())

        verify(accounts).search(eq("user0"), eq("kb"), any<Pageable>())
    }

    /** 기기 목록은 묶음 키 두 개(userid + servicename)로 조회해야 동명이인이 섞이지 않는다. */
    @Test fun 기기_목록은_묶음_키_두_개로_조회한다() {
        whenever(accounts.credentialsOf(anyOrNull(), anyOrNull())).thenReturn(listOf(userinfo(1L)))

        mvc.perform(get("/users/user001/credentials").session(session).with(user(superUser))
                .param("servicename", "kbstar"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/user/credentials"))
            .andExpect(content().string(containsString("user001")))

        verify(accounts).credentialsOf("user001", "kbstar")
    }

    /** 기기 목록에도 민감 컬럼이 실리면 안 된다(UserRow 가 걸러낸다). */
    @Test fun 기기_목록도_민감_컬럼을_노출하지_않는다() {
        whenever(accounts.credentialsOf(anyOrNull(), anyOrNull())).thenReturn(listOf(userinfo(1L)))

        mvc.perform(get("/users/user001/credentials").session(session).with(user(superUser))
                .param("servicename", "kbstar"))
            .andExpect(content().string(not(containsString(FULL_PUBKEY))))
            .andExpect(content().string(not(containsString(FULL_CERT))))
    }

    @Test fun head16CutsAndPassesNull() {
        assertThat(UserView.head16(FULL_PUBKEY)).isEqualTo("MFkwEwYHKoZIzj0C")
        assertThat(UserView.head16("short")).isEqualTo("short")
        assertThat(UserView.head16(null)).isNull()
    }
}
