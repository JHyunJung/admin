package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.service.SystemPropService
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
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

@WebMvcTest(controllers = [SystemPropController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
         SystemPropIdConverter::class,
         com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class SystemPropControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: com.crosscert.fidoadmin.common.SelectedTenant
    @MockitoBean lateinit var service: SystemPropService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun prop(key: String, company: Long, value: String): CcfaSystemProp {
        val p = CcfaSystemProp()
        p.id = CcfaSystemPropId(key, company); p.propValue = value; p.shareType = "YES"
        return p
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


    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/system/props").with(user(companyUser))).andExpect(status().isForbidden())
    }

    /** 목록에는 고객사 컬럼이 없다(세션이 테넌트를 정한다). 상세는 여전히 보여준다. */
    @Test fun listRendersRowsWithPathLinks() {
        whenever(service.defaultSort()).thenReturn(Sort.by("id.propKey"))
        whenever(service.search(any(), any())).thenReturn(PageImpl(listOf(prop("PW_FAIL_LIMIT", 0L, "5"))))
        mvc.perform(get("/system/props").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/list"))
            .andExpect(content().string(containsString("PW_FAIL_LIMIT")))
            .andExpect(content().string(containsString("/system/props/PW_FAIL_LIMIT@0")))
    }

    /** 경로 변수 "PW_FAIL_LIMIT@0" 가 복합키로 변환되어 서비스에 전달된다. */
    @Test fun detailConvertsCompositePathVariable() {
        val id = CcfaSystemPropId("PW_FAIL_LIMIT", 0L)
        whenever(service.get(id)).thenReturn(prop("PW_FAIL_LIMIT", 0L, "5"))
        whenever(companies.name(0L)).thenReturn("전역(시스템)")
        mvc.perform(get("/system/props/PW_FAIL_LIMIT@0").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/detail"))
            .andExpect(content().string(containsString("PW_FAIL_LIMIT")))
            .andExpect(content().string(containsString("전역(시스템)")))
        verify(service).get(id)
    }

    /** 경로 값이 "키@COMPANY_IDX" 형식이 아니면 어떤 자원도 가리키지 않으므로 404. */
    @Test fun malformedPathVariableIsNotFound() {
        mvc.perform(get("/system/props/NO_AT").session(session).with(user(superUser)))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error/404"))
    }

    /**
     * PROP_KEY 는 리다이렉트 경로의 {id} 세그먼트로 그대로 쓰인다. RedirectView 가 "{...}" 를
     * URI 템플릿 변수로 해석하거나(예: "a{x}") "?"/"#" 뒤가 쿼리·프래그먼트로 잘리는 값은
     * 저장 후 리다이렉트가 깨지므로 허용 문자 집합(@Pattern)으로 미리 막는다.
     */
    @Test fun keyWithSlashIsRejected() {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "a/b").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")))
        verify(service, never()).create(any())
    }

    @Test fun keyWithBraceIsRejected() {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "a{x}").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")))
        verify(service, never()).create(any())
    }

    @Test fun keyWithQuestionMarkIsRejected() {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "a?b").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")))
        verify(service, never()).create(any())
    }

    @Test fun keyWithUpperCaseSlashIsRejected() {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "A/B").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")))
        verify(service, never()).create(any())
    }

    /** "new" 는 등록 폼 경로와 겹쳐 예약된 값으로 거부한다. */
    @Test fun keyEqualToNewIsRejected() {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "new").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키로 쓸 수 없는 값입니다: new")))
        verify(service, never()).create(any())
    }

    /** 점으로만 된 값은 경로 세그먼트로서 특수한 의미를 가져 예약된 값으로 거부한다. */
    @Test fun keyOfDotsOnlyIsRejected() {
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "..").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("키로 쓸 수 없는 값입니다: ..")))
        verify(service, never()).create(any())
    }

    @Test fun createRedirectsToCompositeDetail() {
        whenever(service.create(any())).thenReturn(prop("NEW_KEY", 0L, "x"))
        whenever(service.idOf(any())).thenReturn("NEW_KEY@0")
        mvc.perform(post("/system/props").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "NEW_KEY").param("propValue", "x").param("shareType", "NO"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/props/NEW_KEY@0"))
    }

    /** update 리다이렉트는 CrudController 가 id.toString() 으로 만든다. toString 이 경로 값이어야 한다. */
    @Test fun updateRedirectsToCompositeDetail() {
        mvc.perform(post("/system/props/PW_FAIL_LIMIT@0").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "PW_FAIL_LIMIT").param("propValue", "7").param("shareType", "YES"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/system/props/PW_FAIL_LIMIT@0"))
    }

    /** 비밀 키 수정에서 마스크 뒤에 글자를 붙이면 폼 오류로 돌려보내고 저장하지 않는다(진단 106). */
    @Test fun editedMaskOnSecretKeyIsRejected() {
        whenever(service.get(any())).thenReturn(prop("SMTP_PASSWORD", 0L, "real"))
        mvc.perform(post("/system/props/SMTP_PASSWORD@0").session(session).with(user(superUser)).with(csrf())
                .param("propKey", "SMTP_PASSWORD").param("propValue", "********abc").param("shareType", "NO"))
            .andExpect(status().isOk())
            .andExpect(view().name("system/props/form"))
            .andExpect(content().string(containsString("마스크")))
        verify(service, never()).update(any(), any())
    }
}
