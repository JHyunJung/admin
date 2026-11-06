package com.crosscert.fidoadmin.fido.web

import org.mockito.kotlin.argumentCaptor
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
import com.crosscert.fidoadmin.fido.entity.Appserver
import com.crosscert.fidoadmin.fido.service.AppserverService
import java.util.function.Consumer
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@WebMvcTest(controllers = [AppserverController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class)
class AppserverControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: SelectedTenant
    @MockitoBean lateinit var service: AppserverService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun server(idx: Long): Appserver {
        val a = Appserver(); a.idx = idx; a.companyIdx = 1L; a.memberCode = "KB01"
        a.memberId = "kbsvr01"; a.type = "use"; a.note = "스타뱅킹 서버"
        return a
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


    @Test fun listRendersMemberCode() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(any(), any<Pageable>())).thenReturn(PageImpl(listOf(server(1L))))
        whenever(companies.names()).thenReturn(mapOf(1L to "KB국민은행"))
        mvc.perform(get("/appservers").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/list"))
            .andExpect(content().string(containsString("KB01")))
            .andExpect(content().string(containsString("kbsvr01")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))))
    }

    @Test fun blankMemberIdShowsFormAgain() {
        mvc.perform(post("/appservers").with(user(companyUser)).with(csrf())
                .param("memberId", "").param("type", "use"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/form"))
    }



    /** 수정 폼의 읽기 전용 멤버코드를 변조해도 DB 값이 그대로이고, 중복 검사도 DB 값으로 돈다. */
    @Test fun updateIgnoresPostedMemberCodeAndChecksWithStoredCode() {
        val stored = server(5L) // memberCode "KB01"
        whenever(service.get(5L)).thenReturn(stored)
        whenever(service.existsDuplicate("KB01", "ID01", 5L)).thenReturn(false)
        whenever(service.update(eq(5L), any<Consumer<Appserver>>())).thenAnswer { inv ->
            val mutator = inv.getArgument<Consumer<Appserver>>(1)
            mutator.accept(stored)
            stored
        }
        mvc.perform(post("/appservers/5").with(user(companyUser)).with(csrf())
                .param("memberCode", "HACK").param("memberId", "ID01").param("type", "use"))
            .andExpect(status().is3xxRedirection())
        verify(service).existsDuplicate("KB01", "ID01", 5L)
        assertThat(stored.memberCode).isEqualTo("KB01")
        assertThat(stored.memberId).isEqualTo("ID01")
    }

    @Test fun updateRejectsDuplicateWithStoredCode() {
        whenever(service.get(5L)).thenReturn(server(5L))
        whenever(service.existsDuplicate("KB01", "ID01", 5L)).thenReturn(true)
        mvc.perform(post("/appservers/5").with(user(companyUser)).with(csrf())
                .param("memberId", "ID01").param("type", "use"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido/appserver/form"))
            .andExpect(content().string(containsString("이미 등록된 코드와 ID값 입니다.")))
    }

    @Test fun createIgnoresPostedMemberCodeAndRedirects() {
        whenever(service.create(any())).thenReturn(server(9L))
        whenever(service.idOf(any())).thenReturn("9")
        mvc.perform(post("/appservers").with(user(companyUser)).with(csrf())
                .param("memberCode", "TYPED").param("memberId", "kbsvr01").param("type", "use"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/appservers/9"))
        val captor = argumentCaptor<Appserver>()
        verify(service).create(captor.capture())
        assertThat(captor.lastValue.memberCode).isNull()
        verify(service, never()).existsDuplicate(anyOrNull(), anyOrNull(), isNull())
    }

    @Test fun newFormHasNoMemberCodeInput() {
        mvc.perform(get("/appservers/new").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("name=\"memberCode\""))))
            .andExpect(content().string(containsString("저장 시 자동 생성됩니다")))
    }

    @Test fun editFormShowsMemberCodeReadOnly() {
        whenever(service.get(2L)).thenReturn(server(2L))
        mvc.perform(get("/appservers/2/edit").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("KB01")))
            .andExpect(content().string(containsString("readonly")))
    }

    @Test fun detailRendersColumns() {
        whenever(service.get(2L)).thenReturn(server(2L))
        whenever(companies.name(1L)).thenReturn("KB국민은행")
        mvc.perform(get("/appservers/2").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("스타뱅킹 서버")))
            .andExpect(content().string(containsString("KB국민은행")))
    }
}
