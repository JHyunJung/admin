package com.crosscert.fidoadmin.dashboard

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import java.time.LocalDate
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [DashboardController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class, GlobalExceptionHandler::class, com.crosscert.fidoadmin.common.TenantContext::class, com.crosscert.fidoadmin.common.SelectedTenant::class)
class DashboardControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var stats: StatisticsQueryService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    private val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    @Test fun rendersTotalsAndSerializedRows() {
        whenever(stats.lastStatDate(anyOrNull())).thenReturn(Optional.of(LocalDate.now()))
        whenever(stats.serviceNames()).thenReturn(listOf("kbstar"))
        whenever(stats.daily(anyOrNull())).thenReturn(listOf(DailyStat(LocalDate.of(2026, 9, 1), 1200, 30, 0, 0, 10, 0, 0, 0)))
        val u = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)
        mvc.perform(get("/").with(user(u)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("1,200")))
            .andExpect(content().string(containsString("성공률 97.6%")))
            .andExpect(content().string(containsString("\"date\":\"2026-09-01\"")))
    }

    /**
     * 필터는 조회 버튼 없이 바뀌는 즉시 조회한다(admin.js 의 data-auto-submit).
     * 자동 제출 동작 자체는 브라우저 몫이라 여기서는 표식이 붙어 있는지만 고정한다 —
     * 표식이 빠지면 화면은 멀쩡한데 날짜를 바꿔도 아무 일이 없는 상태로 돌아간다.
     *
     * 조회 버튼은 함께 남는다. 스크립트가 로드되지 않는 환경의 유일한 조회 수단이다.
     */
    @Test fun 필터는_자동_조회_표식을_가진다() {
        whenever(stats.lastStatDate(anyOrNull())).thenReturn(Optional.of(LocalDate.now()))
        whenever(stats.serviceNames()).thenReturn(listOf("kbstar"))
        whenever(stats.daily(anyOrNull())).thenReturn(listOf())
        val u = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)
        mvc.perform(get("/").with(user(u)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-auto-submit")))
            .andExpect(content().string(containsString("조회")))
    }

    /** 요약 카드는 지표마다 색 띠와 아이콘으로 구분한다. */
    @Test fun summaryCardsAreColorCoded() {
        whenever(stats.lastStatDate(anyOrNull())).thenReturn(Optional.of(LocalDate.now()))
        whenever(stats.serviceNames()).thenReturn(listOf("kbstar"))
        whenever(stats.daily(anyOrNull())).thenReturn(listOf(DailyStat(LocalDate.of(2026, 9, 1), 1200, 30, 5, 1, 10, 2, 3, 1)))
        val u = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)
        mvc.perform(get("/").with(user(u)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("fa-stat")))
            .andExpect(content().string(containsString("fa-stat-auth")))
            .andExpect(content().string(containsString("fa-stat-reg")))
            .andExpect(content().string(containsString("fa-stat-dereg")))
            .andExpect(content().string(containsString("fa-stat-tc")))
            // 카드마다 아이콘이 붙는다
            .andExpect(content().string(containsString("bi bi-shield-check")))
            // 네 지표의 수치는 그대로 남는다
            .andExpect(content().string(containsString("1,200")))
            .andExpect(content().string(containsString("성공률 97.6%")))
    }

    /** 통계가 아예 없는 고객사(마지막 집계일 없음): 원인을 알린다. */
    @Test fun 통계가_없는_고객사는_원인을_알린다() {
        whenever(stats.serviceNames()).thenReturn(listOf())
        whenever(stats.daily(anyOrNull())).thenReturn(listOf())
        whenever(stats.lastStatDate(anyOrNull())).thenReturn(Optional.empty())
        mvc.perform(get("/").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("이 고객사에는 통계 데이터가 없습니다")))
    }

    /** 집계 단위 선택칸은 없다. GROUPBY 는 시간 구간이라 고를 대상이 아니다. */
    @Test fun 집계_단위_선택칸이_없다() {
        whenever(stats.serviceNames()).thenReturn(listOf("kbstar"))
        whenever(stats.daily(anyOrNull())).thenReturn(listOf())
        mvc.perform(get("/").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("집계 단위"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"groupby\""))))
    }

    /** 기간에 데이터가 없으면 기간과 마지막 집계일을 알린다. */
    @Test fun 기간에_데이터가_없으면_마지막_집계일을_알린다() {
        whenever(stats.lastStatDate(anyOrNull())).thenReturn(Optional.of(LocalDate.now()))
        whenever(stats.serviceNames()).thenReturn(listOf("kbstar"))
        whenever(stats.daily(anyOrNull())).thenReturn(listOf())
        whenever(stats.lastStatDate(anyOrNull())).thenReturn(Optional.of(LocalDate.of(2026, 9, 19)))
        mvc.perform(get("/").param("fromDate", "2026-01-01").param("toDate", "2026-01-31").with(user(companyUser)))
            .andExpect(content().string(containsString("선택한 기간(2026-01-01 ~ 2026-01-31)에 데이터가 없습니다.")))
            .andExpect(content().string(containsString("마지막 집계일: 2026-09-19")))
    }

    /** 데이터가 있지만 마지막 집계일이 종료일보다 이르면 배치 지연을 알린다. */
    @Test fun 마지막_집계일_이후가_비면_알린다() {
        whenever(stats.lastStatDate(anyOrNull())).thenReturn(Optional.of(LocalDate.now()))
        whenever(stats.serviceNames()).thenReturn(listOf("kbstar"))
        whenever(stats.daily(anyOrNull())).thenReturn(listOf(DailyStat(LocalDate.of(2026, 9, 19), 1, 0, 0, 0, 0, 0, 0, 0)))
        whenever(stats.lastStatDate(anyOrNull())).thenReturn(Optional.of(LocalDate.of(2026, 9, 19)))
        mvc.perform(get("/").param("fromDate", "2026-09-01").param("toDate", "2026-09-28").with(user(companyUser)))
            .andExpect(content().string(containsString("마지막 집계일은 2026-09-19 입니다")))
    }

    @Test fun 대시보드_차트는_V2_색과_선_모양을_쓴다() {
        whenever(stats.lastStatDate(anyOrNull())).thenReturn(Optional.of(LocalDate.now()))
        whenever(stats.serviceNames()).thenReturn(listOf("kbstar"))
        whenever(stats.daily(anyOrNull())).thenReturn(listOf(DailyStat(LocalDate.of(2026, 9, 1), 1200, 30, 0, 0, 10, 0, 0, 0)))
        val u = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)
        val html = mvc.perform(get("/").with(user(u)))
            .andExpect(status().isOk()).andReturn().response.contentAsString
        assertThat(html).contains("borderColor: '#1F5EDB'", "borderColor: '#B42318'", "borderDash: [6, 4]", "borderColor: '#0F1B2D'",
            "class=\"fa-chart-card\"")
    }
}
