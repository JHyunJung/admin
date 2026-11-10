package com.crosscert.fidoadmin.signup

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
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import java.time.LocalDateTime
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
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

@WebMvcTest(controllers = [SignupAdminController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class,
    GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class)
class SignupAdminControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: SignupService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun pending(): CcfaManager {
        val m = CcfaManager()
        m.idx = 5L
        m.userId = "newbie"
        m.userNm = "홍길동"
        m.userEmail = "hong@kb.local"
        m.userPhone = "010-1234-5678"
        m.status = SignupPolicy.STATUS_PENDING
        m.companyIdx = SignupPolicy.UNASSIGNED_COMPANY_IDX
        m.etc = "신청 사유: 업무 담당자입니다"
        m.createdtime = LocalDateTime.of(2026, 9, 17, 10, 0)
        return m
    }

    private fun company(idx: Long, name: String): CcfaCompany {
        val c = CcfaCompany()
        c.idx = idx
        c.companyName = name
        return c
    }

    private fun twoCompanies() {
        whenever(companies.all()).thenReturn(listOf(company(0L, "전역"), company(1L, "KB국민은행")))
    }

    @Test fun listShowsPendingApplications() {
        whenever(service.pending()).thenReturn(listOf(pending()))
        twoCompanies()
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("newbie")))
            .andExpect(content().string(containsString("홍길동")))
            .andExpect(content().string(containsString("hong@kb.local")))
            .andExpect(content().string(containsString("업무 담당자입니다")))
    }

    /** 승인 대기 목록 화면에 USER_PW 해시가 나가지 않는다. */
    @Test fun listDoesNotExposePasswordHash() {
        val hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"
        val m = pending()
        m.userPw = hash
        whenever(service.pending()).thenReturn(listOf(m))
        twoCompanies()
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("newbie")))
            .andExpect(content().string(not(containsString(hash))))
    }

    @Test fun listShowsEmptyMessageWhenNothingPending() {
        whenever(service.pending()).thenReturn(listOf())
        twoCompanies()
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("승인 대기 중인 신청이 없습니다")))
    }

    /**
     * 승인 화면의 고객사 선택 목록에서 전역(IDX 0)을 제외한다.
     * 옵션 태그 자체를 찾는다. 화면 어딘가의 `value="0"` 과 섞이지 않게 하기 위해서다.
     */
    @Test fun companySelectExcludesGlobalCompany() {
        whenever(service.pending()).thenReturn(listOf(pending()))
        twoCompanies()
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<option value=\"1\">KB국민은행</option>")))
            .andExpect(content().string(not(containsString("<option value=\"0\""))))
            .andExpect(content().string(not(containsString(">전역</option>"))))
    }

    /** 거절 사유 입력란은 ETC 바이트 여유(317바이트)를 넘겨 쓰도록 유도하지 않는다. */
    @Test fun rejectReasonInputIsLengthLimited() {
        whenever(service.pending()).thenReturn(listOf(pending()))
        twoCompanies()
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("maxlength=\"100\"")))
    }

    /**
     * 거절 폼은 확인 모달에 연결돼 있어야 한다.
     *
     * 이 화면의 거절 폼은 텍스트 입력이 하나뿐이고 제출 버튼이 없다. 그런 폼은 입력란에서
     * Enter 만 쳐도 브라우저가 암묵적 제출(implicit submission)을 일으킨다. 실제로 그 경로로
     * 확인 모달이 통째로 건너뛰어져 되돌릴 수 없는 거절이 바로 실행되는 결함이 있었다.
     *
     * 막는 일 자체는 admin.js 가 폼의 submit 이벤트를 가로채서 한다. 브라우저가 Enter 를
     * 암묵적 제출로 바꾸는 동작은 MockMvc 로 재현할 수 없으므로, 여기서는 그 가드가 붙을
     * 자리인 data-confirm-form 연결만 지킨다. 이 속성이 사라지면 admin.js 는 이 폼을
     * 아예 모르게 되고 Enter 도 클릭도 확인 없이 지나간다.
     */
    @Test fun rejectFormIsWiredToConfirmModal() {
        whenever(service.pending()).thenReturn(listOf(pending()))
        twoCompanies()
        mvc.perform(get("/signups").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"rejectForm5\"")))
            .andExpect(content().string(containsString("data-confirm-form=\"rejectForm5\"")))
            // 거절은 되돌릴 수 없으므로 확인 문구가 그 사실을 알려야 한다.
            .andExpect(content().string(containsString("되돌릴 수 없습니다")))
    }

    @Test fun approveRedirectsWithFlash() {
        whenever(service.approve(5L, 1L)).thenReturn(pending())
        mvc.perform(post("/signups/5/approve").with(user(superUser)).with(csrf())
                .param("companyIdx", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashSuccess", "가입을 승인했습니다."))
        verify(service).approve(5L, 1L)
    }

    /** 서비스가 입력을 거부하면(IllegalArgumentException) 오류 플래시로 돌려준다(500 이 아니다). */
    @Test fun approveShowsErrorWhenCompanyIsRejected() {
        whenever(service.approve(5L, 0L))
            .thenThrow(IllegalArgumentException("전역(IDX 0) 고객사로는 승인할 수 없습니다."))
        mvc.perform(post("/signups/5/approve").with(user(superUser)).with(csrf())
                .param("companyIdx", "0"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashError", "전역(IDX 0) 고객사로는 승인할 수 없습니다."))
    }

    /** 이미 처리된 신청(IllegalStateException)도 같은 방식으로 오류 플래시가 된다. */
    @Test fun approveShowsErrorWhenAlreadyProcessed() {
        whenever(service.approve(5L, 1L))
            .thenThrow(IllegalStateException("이미 처리된 신청입니다(승인대기 상태가 아닙니다): newbie"))
        mvc.perform(post("/signups/5/approve").with(user(superUser)).with(csrf())
                .param("companyIdx", "1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashError",
                "이미 처리된 신청입니다(승인대기 상태가 아닙니다): newbie"))
    }

    /**
     * 고객사를 고르지 않으면 빈 문자열이 온다. 400(흰 오류 화면)이 아니라 null 로 서비스에 넘겨
     * 서비스의 "고객사를 선택해야 합니다" 거부가 오류 플래시로 보이게 한다.
     */
    @Test fun approveWithoutCompanyShowsError() {
        whenever(service.approve(5L, null)).thenThrow(IllegalArgumentException("고객사를 선택해야 합니다."))
        mvc.perform(post("/signups/5/approve").with(user(superUser)).with(csrf())
                .param("companyIdx", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashError", "고객사를 선택해야 합니다."))
        verify(service).approve(5L, null)
    }

    @Test fun rejectRedirectsWithFlash() {
        whenever(service.reject(5L, "소속 확인 불가")).thenReturn(pending())
        mvc.perform(post("/signups/5/reject").with(user(superUser)).with(csrf())
                .param("reason", "소속 확인 불가"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashSuccess", "가입을 거절했습니다."))
        verify(service).reject(5L, "소속 확인 불가")
    }

    @Test fun rejectShowsErrorWhenAlreadyProcessed() {
        whenever(service.reject(anyLong(), anyString()))
            .thenThrow(IllegalStateException("이미 처리된 신청입니다(승인대기 상태가 아닙니다): newbie"))
        mvc.perform(post("/signups/5/reject").with(user(superUser)).with(csrf())
                .param("reason", "소속 확인 불가"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/signups"))
            .andExpect(flash().attribute("flashError",
                "이미 처리된 신청입니다(승인대기 상태가 아닙니다): newbie"))
    }

    /**
     * 권한 상승이 걸린 화면이므로 CSRF 보호가 살아 있는지 못 박는다. 나머지 테스트가 모두
     * `.with(csrf())` 를 붙이기 때문에, 이 테스트가 없으면 보호가 꺼져도 알 수 없다.
     */
    @Test fun approveWithoutCsrfIsForbidden() {
        mvc.perform(post("/signups/5/approve").with(user(superUser))
                .param("companyIdx", "1"))
            .andExpect(status().isForbidden())
        verify(service, never()).approve(anyLong(), anyLong())
    }

    /** 일반 고객사 계정은 접근할 수 없다. */
    @Test fun companyUserIsForbidden() {
        mvc.perform(get("/signups").with(user(companyUser)))
            .andExpect(status().isForbidden())
        mvc.perform(post("/signups/5/approve").with(user(companyUser)).with(csrf())
                .param("companyIdx", "1"))
            .andExpect(status().isForbidden())
        mvc.perform(post("/signups/5/reject").with(user(companyUser)).with(csrf())
                .param("reason", "안 됨"))
            .andExpect(status().isForbidden())
        verify(service, never()).approve(anyLong(), anyLong())
        verify(service, never()).reject(anyLong(), anyString())
    }

    @Test fun anonymousIsRedirectedToLogin() {
        mvc.perform(get("/signups"))
            .andExpect(status().is3xxRedirection())
        verify(service, never()).pending()
    }
}
