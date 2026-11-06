package com.crosscert.fidoadmin.manager.web

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
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy
import com.crosscert.fidoadmin.manager.service.ManagerService
import com.crosscert.fidoadmin.manager.service.SuperManagerService
import java.util.Optional
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull
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

/**
 * /managers/super 는 ManagerController 의 "/managers/{id}" 와 URL 이 겹칠 수 있어 보인다.
 * 이 슬라이스는 두 컨트롤러를 함께 등록해 Spring 이 실제로 어느 쪽으로 라우팅하는지
 * (규칙에 의존하지 않고) 확인한다.
 */
@WebMvcTest(controllers = [SuperManagerController::class, ManagerController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class,
    GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class)
class SuperManagerControllerWebTest {

    companion object {
        val PW_HASH: String = "a".repeat(64)
    }

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: SelectedTenant
    @MockitoBean lateinit var service: SuperManagerService
    @MockitoBean lateinit var managerService: ManagerService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 5L, "KB", true, true)

    private fun manager(idx: Long, userId: String): CcfaManager {
        val m = CcfaManager(); m.idx = idx; m.userId = userId; m.userPw = PW_HASH
        m.userNm = "슈퍼관리자"; m.companyIdx = 0L; m.status = "활성"; m.login = "OFF-LINE"
        return m
    }

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/managers/super").with(user(companyUser))).andExpect(status().isForbidden())
    }

    /**
     * 시스템 영역이므로 고객사 선택 없이 열린다. TenantSelectionInterceptor 는 TENANT 영역만
     * 가로채는데, MenuRegistry.areaOf("/managers/super") 가 SYSTEM 이어야 이 테스트가 통과한다.
     */
    @Test fun 미선택_SUPER_도_열_수_있다() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(anyOrNull(), anyOrNull())).thenReturn(PageImpl(listOf()))
        mvc.perform(get("/managers/super").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/super/list"))
    }

    /**
     * 라우팅 확인: "/managers/super" 는 SuperManagerController 로 가야 한다.
     * ManagerController 의 "/managers/{id}" 로 갔다면 {id}=Long 변환에 실패해 404가 되거나,
     * (막았어야 할) ManagerService 가 호출된다. 여기서는 뷰 이름으로 목적지를 확정한다.
     */
    @Test fun 슈퍼관리자_경로는_SuperManagerController_로_라우팅된다() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(anyOrNull(), anyOrNull())).thenReturn(PageImpl(listOf(manager(9L, "superadmin"))))
        mvc.perform(get("/managers/super").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/super/list"))
            .andExpect(content().string(containsString("superadmin")))
        verify(managerService, never()).search(anyOrNull(), anyOrNull())
    }

    /** 민감 컬럼 USER_PW 는 목록·상세 어디에도 실리지 않는다. */
    @Test fun listNeverExposesPasswordHash() {
        whenever(service.defaultSort()).thenReturn(Sort.by("idx"))
        whenever(service.search(anyOrNull(), anyOrNull())).thenReturn(PageImpl(listOf(manager(9L, "superadmin"))))
        mvc.perform(get("/managers/super").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("superadmin")))
            .andExpect(content().string(not(containsString(PW_HASH))))
    }

    @Test fun detailShowsLockStateAndHidesPasswordHash() {
        whenever(service.get(9L)).thenReturn(manager(9L, "superadmin"))
        val p = CcfaManagerPwPolicy(); p.accountLock = "Y"; p.pwFailCnt = 3L
        whenever(service.lockState("superadmin")).thenReturn(Optional.of(p))
        mvc.perform(get("/managers/super/9").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/super/detail"))
            .andExpect(content().string(containsString("잠김")))
            .andExpect(content().string(not(containsString(PW_HASH))))
    }

    @Test fun createWithoutPasswordShowsFormWithMessage() {
        mvc.perform(post("/managers/super").with(user(superUser)).with(csrf())
                .param("userId", "newsuper").param("companyIdx", "0").param("status", "활성"))
            .andExpect(status().isOk())
            .andExpect(view().name("manager/super/form"))
            .andExpect(content().string(containsString("비밀번호는 필수입니다.")))
    }

    @Test fun createEncodesPasswordAndRedirects() {
        val saved = manager(20L, "newsuper")
        whenever(service.create(anyOrNull())).thenReturn(saved)
        whenever(service.idOf(anyOrNull())).thenReturn("20")
        mvc.perform(post("/managers/super").with(user(superUser)).with(csrf())
                .param("userId", "newsuper").param("companyIdx", "0").param("status", "활성")
                .param("password", "Secret1234!").param("passwordConfirm", "Secret1234!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/super/20"))
    }

    @Test fun unlockRedirectsToDetailWithFlash() {
        mvc.perform(post("/managers/super/9/unlock").with(user(superUser)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/super/9"))
            .andExpect(flash().attribute("flashSuccess", "잠금이 해제되었습니다."))
        verify(service).unlock(9L)
    }

    @Test fun postWithoutCsrfIsForbidden() {
        mvc.perform(post("/managers/super/9/unlock").with(user(superUser))).andExpect(status().isForbidden())
    }

    /**
     * 자기 자신은 삭제할 수 없다. 이 화면에서 더 중요한 규칙이다 — 복구 경로가 없다.
     * 서비스가 IllegalStateException 을 던지면 컨트롤러가 500 대신 flashError 로 되돌린다.
     */
    @Test fun 자기_자신은_삭제할_수_없다() {
        doThrow(IllegalStateException("자기 자신은 삭제할 수 없습니다."))
            .`when`(service).delete(1L)
        mvc.perform(post("/managers/super/1/delete").with(user(superUser)).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/managers/super/1"))
            .andExpect(flash().attribute("flashError", "자기 자신은 삭제할 수 없습니다."))
    }
}
