package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode
import com.crosscert.fidoadmin.fido2.service.Fido2DemoAccessCodeService
import java.util.function.Consumer
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@WebMvcTest(controllers = [Fido2DemoAccessCodeController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class,
    com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class Fido2DemoAccessCodeControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: Fido2DemoAccessCodeService
    @MockitoBean lateinit var companies: com.crosscert.fidoadmin.company.service.CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private fun seedCode(): Fido2DemoAccessCode {
        val c = Fido2DemoAccessCode()
        c.accesscode = "DEMO-0001"; c.vendorname = "벤더A"; c.starttime = 1767225600L; c.endtime = 1798761600L
        c.status = "E"; c.note = "데모용"
        return c
    }

    @Test fun companyRoleIsForbidden() {
        mvc.perform(get("/fido2/demo-access-codes").with(user(companyUser))).andExpect(status().isForbidden())
    }

    /** epoch 초 1767225600 은 서울 2026-01-01 09:00 으로 보여야 한다. */
    @Test fun listShowsEpochAsSeoulDateTime() {
        whenever(service.defaultSort()).thenReturn(Sort.by("accesscode"))
        whenever(service.search(anyOrNull(), anyOrNull())).thenReturn(PageImpl(listOf(seedCode())))
        mvc.perform(get("/fido2/demo-access-codes").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/list"))
            .andExpect(content().string(containsString("DEMO-0001")))
            .andExpect(content().string(containsString("2026-01-01 09:00")))
    }

    @Test fun detailShowsRawEpochToo() {
        whenever(service.get("DEMO-0001")).thenReturn(seedCode())
        mvc.perform(get("/fido2/demo-access-codes/DEMO-0001").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/detail"))
            .andExpect(content().string(containsString("1767225600")))
            .andExpect(content().string(containsString("2026-01-01 09:00")))
    }

    @Test fun endBeforeStartShowsFormWithMessage() {
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "DEMO-0003").param("status", "E")
                .param("starttime", "2026-01-02T00:00").param("endtime", "2026-01-01T00:00"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/form"))
            .andExpect(content().string(containsString("종료일시는 시작일시보다 빠를 수 없습니다.")))
    }

    @Test fun blankAccessCodeShowsFormAgain() {
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "").param("status", "E"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/form"))
    }

    @Test fun createRedirectsToDetailByAccessCode() {
        val saved = Fido2DemoAccessCode(); saved.accesscode = "DEMO-0003"
        whenever(service.create(anyOrNull())).thenReturn(saved)
        whenever(service.idOf(anyOrNull())).thenReturn("DEMO-0003")
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "DEMO-0003").param("vendorname", "벤더C").param("status", "E")
                .param("starttime", "2026-01-01T09:00").param("endtime", "2026-12-31T23:59"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/fido2/demo-access-codes/DEMO-0003"))
    }

    /** 초가 있는 epoch 값(1770000000)을 수정 폼에 채웠을 때 초까지 표시되어야 한다(초 유실 방지). */
    @Test fun editFormRendersSecondsFromEpoch() {
        val c = seedCode()
        c.starttime = 1770000000L
        val expected = EpochSeconds.fromEpoch(1770000000L)!!
        whenever(service.get("DEMO-0001")).thenReturn(c)
        val expectedValue = expected.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        mvc.perform(get("/fido2/demo-access-codes/DEMO-0001/edit").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/form"))
            .andExpect(content().string(containsString(expectedValue)))
    }

    /** 초가 있는 값으로 수정 제출하면 초까지 보존된 epoch 로 다시 변환되어야 한다. */
    @Test fun updateWithSecondsRoundTripsToSameEpoch() {
        val existing = seedCode()
        existing.starttime = 1770000000L
        whenever(service.get("DEMO-0001")).thenReturn(existing)
        val start = EpochSeconds.fromEpoch(1770000000L)!!
        val startValue = start.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

        mvc.perform(post("/fido2/demo-access-codes/DEMO-0001").with(user(superUser)).with(csrf())
                .param("accesscode", "DEMO-0001").param("vendorname", "벤더A").param("status", "E")
                .param("starttime", startValue).param("endtime", "2026-12-31T23:59:59"))
            .andExpect(status().is3xxRedirection())

        val captor = argumentCaptor<Consumer<Fido2DemoAccessCode>>()
        verify(service).update(eq("DEMO-0001"), captor.capture())
        val target = Fido2DemoAccessCode()
        captor.lastValue.accept(target)
        assertThat(target.starttime).isEqualTo(1770000000L)
    }

    /** ACCESSCODE 는 CRUD 경로의 {id} 세그먼트라 URL에 위험한 문자는 거부해야 한다. */
    @Test fun accessCodeWithSlashIsRejected() {
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "DEMO/0003").param("status", "E"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/form"))
            .andExpect(content().string(containsString("접근코드는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")))
        verify(service, never()).create(anyOrNull())
    }

    /** "new" 는 등록 폼 경로("/fido2/demo-access-codes/new")와 겹쳐 상세 조회가 그 경로로 잘못 라우팅된다. */
    @Test fun accessCodeNewIsRejected() {
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "new").param("status", "E"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/form"))
            .andExpect(content().string(containsString("접근코드로 쓸 수 없는 값입니다: new")))
        verify(service, never()).create(anyOrNull())
    }

    /** 점으로만 된 값은 경로 세그먼트로서 특수한 의미(".."=상위 경로)를 가져 링크가 깨진다. */
    @Test fun accessCodeDotsOnlyIsRejected() {
        mvc.perform(post("/fido2/demo-access-codes").with(user(superUser)).with(csrf())
                .param("accesscode", "..").param("status", "E"))
            .andExpect(status().isOk())
            .andExpect(view().name("fido2/demo-access-code/form"))
            .andExpect(content().string(containsString("접근코드로 쓸 수 없는 값입니다: ..")))
        verify(service, never()).create(anyOrNull())
    }
}
