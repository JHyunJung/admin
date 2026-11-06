package com.crosscert.fidoadmin.company.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.service.CompanyService
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import java.util.Optional
import java.util.function.Consumer
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@WebMvcTest(controllers = [CompanyController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class CompanyControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: CompanyService
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    @Test fun listRendersRows() {
        val c = CcfaCompany(); c.idx = 1L; c.companyName = "KB국민은행"
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(anyOrNull(), anyOrNull())).thenReturn(PageImpl(listOf(c)))
        mvc.perform(get("/companies").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/list"))
            .andExpect(content().string(containsString("KB국민은행")))
    }

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/companies").with(user(companyUser))).andExpect(status().isForbidden())
    }

    /** /companies 는 SYSTEM 영역이라 SUPER 는 선택기 대신 "시스템 관리" 뱃지를 본다. */
    @Test fun 시스템_화면에는_시스템_뱃지가_보인다() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(anyOrNull(), anyOrNull())).thenReturn(PageImpl(listOf()))
        mvc.perform(get("/companies").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("시스템 관리")))
            .andExpect(content().string(not(containsString("tenant-switcher"))))
    }

    @Test fun createWithBlankNameShowsFormAgain() {
        mvc.perform(post("/companies").with(user(superUser)).with(csrf()).param("companyName", "").param("enableType", "Y")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/form"))
    }

    /**
     * 길이 초과처럼 companyName 이외의 필드에서 검증이 실패해도 이유가 화면에 보여야 한다.
     * 전에는 companyName·수량 필드만 메시지를 렌더링해, 저장이 조용히 실패하는 것처럼 보였다.
     */
    @Test fun validationErrorOnAnyFieldIsShownToUser() {
        mvc.perform(post("/companies").with(user(superUser)).with(csrf())
                .param("companyName", "정상이름").param("enableType", "Y")
                .param("vendorCode", "a".repeat(21))   // VARCHAR2(20)
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/form"))
            .andExpect(content().string(containsString("바이트를 넘을 수 없습니다")))
    }

    /** 한글은 1자가 3바이트다. 문자 수로 검증하면 저장 단계에서 ORA-12899 가 난다. */
    @Test fun koreanNameOverByteLimitIsRejectedWithMessage() {
        mvc.perform(post("/companies").with(user(superUser)).with(csrf())
                .param("companyName", "가".repeat(86))  // 258 bytes > 256
                .param("enableType", "Y")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/form"))
            .andExpect(content().string(containsString("바이트를 넘을 수 없습니다")))
    }

    @Test fun createRedirectsToDetail() {
        val saved = CcfaCompany(); saved.idx = 77L
        whenever(service.create(anyOrNull())).thenReturn(saved)
        whenever(service.idOf(anyOrNull())).thenReturn("77")
        mvc.perform(post("/companies").with(user(superUser)).with(csrf()).param("companyName", "신규").param("enableType", "Y")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/companies/77"))
    }

    @Test fun postWithoutCsrfIsForbidden() {
        mvc.perform(post("/companies").with(user(superUser)).param("companyName", "x")).andExpect(status().isForbidden())
    }

    /** @PathVariable Long 으로 바꿀 수 없는 값(숫자가 아님)은 어떤 자원도 가리키지 않으므로 404. */
    @Test fun nonNumericIdIsNotFound() {
        mvc.perform(get("/companies/abc").with(user(superUser)))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error/404"))
    }

    private fun other(idx: Long, name: String): CcfaCompany {
        val c = CcfaCompany(); c.idx = idx; c.companyName = name; return c
    }

    /** VENDOR_CODE 는 DB 유니크 제약이 없어 화면에서 막는다. */
    @Test fun duplicateVendorCodeIsRejectedOnCreate() {
        whenever(service.findByVendorCode("V01")).thenReturn(Optional.of(other(9L, "다른회사")))

        mvc.perform(post("/companies").with(user(superUser)).with(csrf())
                .param("companyName", "새회사").param("enableType", "Y").param("vendorCode", "V01")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().isOk())
            .andExpect(view().name("company/company/form"))
            .andExpect(content().string(containsString("이미 사용 중인 업체 코드")))

        verify(service, never()).create(anyOrNull())
    }

    /** 수정 때는 자기 자신의 코드를 중복으로 보지 않는다. */
    @Test fun ownVendorCodeIsAllowedOnUpdate() {
        whenever(service.findByVendorCode("V01")).thenReturn(Optional.of(other(9L, "나")))

        mvc.perform(post("/companies/9").with(user(superUser)).with(csrf())
                .param("companyName", "나").param("enableType", "Y").param("vendorCode", "V01")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/companies/9"))

        verify(service).update(eq(9L), anyOrNull<Consumer<CcfaCompany>>())
    }

    @Test fun createMessageWarnsAllAaidsDisabled() {
        val saved = CcfaCompany(); saved.idx = 77L
        whenever(service.create(anyOrNull())).thenReturn(saved)
        whenever(service.idOf(anyOrNull())).thenReturn("77")
        mvc.perform(post("/companies").with(user(superUser)).with(csrf()).param("companyName", "신규").param("enableType", "Y")
                .param("maxAppid", "0").param("maxAppserver", "0").param("maxUser", "0"))
            .andExpect(flash().attribute("flashSuccess",
                "등록되었습니다. 모든 AAID 가 비활성 상태로 시작합니다. AAID(정책) 화면에서 허용할 인증기를 켜세요."))
    }
}
