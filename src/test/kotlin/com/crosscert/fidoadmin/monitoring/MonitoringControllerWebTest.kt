package com.crosscert.fidoadmin.monitoring

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
import java.time.LocalDate
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [MonitoringController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class,
    GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class)
class MonitoringControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @MockitoBean lateinit var service: MonitoringService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: LoginSuccessHandler
    @MockitoBean lateinit var failure: LoginFailureHandler
    @MockitoBean lateinit var logout: AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: ManagerUserDetailsService

    private val superUser = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    private val companyUser = ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true)

    private val asOf = LocalDateTime.of(2026, 11, 25, 14, 30)
    private val cur = WeekTotals(912, 88, 97, 3, 20)
    private val prev = WeekTotals(976, 24, 50, 0, 0)

    private fun row(
        name: String = "A은행",
        status: LiveStatus = LiveStatus.DOWN,
        recent: Long? = 0,
        baseline: Long? = 412,
        today: Long? = 1500,
        last: LocalDateTime? = LocalDateTime.of(2026, 11, 25, 13, 2),
        recentErrors: Long? = 1,
        todayErrors: Long? = 2,
    ) = LiveRow(10L, name, status, recent, baseline, today, last, recentErrors, todayErrors)

    private fun board(
        live: List<LiveRow> = listOf(row()),
        logs: ColumnState = ColumnState.OK,
        exceptions: ColumnState = ColumnState.OK,
        baselineDate: LocalDate? = LocalDate.of(2026, 11, 24),
        common: CommonRow? = null,
        staleNotice: String? = null,
        week: WeekTable? = WeekTable(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 23),
            listOf(WeekRow(10L, "A은행", cur, prev, true))),
    ) = MonitoringBoard(asOf, 60, logs, exceptions, baselineDate, common, live, staleNotice, week)

    private fun html(b: MonitoringBoard): String {
        whenever(service.board()).thenReturn(b)
        return mvc.perform(get("/monitoring").with(user(superUser)))
            .andExpect(status().isOk()).andReturn().response.contentAsString
    }

    @Test fun SUPER는_고객사를_고르지_않아도_열린다() {
        whenever(service.board()).thenReturn(board())
        mvc.perform(get("/monitoring").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("A은행")))
            .andExpect(content().string(containsString("끊김")))
            .andExpect(content().string(containsString("412")))
            .andExpect(content().string(containsString("기준 시각 2026-11-25 14:30 · 60초마다 갱신된 값입니다.")))
            .andExpect(content().string(containsString("기준일: 2026-11-24 (화)")))
    }

    @Test fun COMPANY_운영자는_403() {
        mvc.perform(get("/monitoring").with(user(companyUser)))
            .andExpect(status().isForbidden())
        verifyNoInteractions(service)
    }

    @Test fun 로그인하지_않으면_로그인으로_간다() {
        mvc.perform(get("/monitoring"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"))
        verifyNoInteractions(service)
    }

    @Test fun 실패한_열은_확인_불가로_보인다() {
        val b = board(
            live = listOf(row(status = LiveStatus.UNKNOWN, recent = null, baseline = null, today = null, last = null)),
            logs = ColumnState.FAILED,
        )
        whenever(service.board()).thenReturn(b)
        mvc.perform(get("/monitoring").with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("확인 불가")))
            .andExpect(content().string(not(containsString("끊김"))))
    }

    @Test fun 꺼진_열은_사용_안_함으로_보인다() {
        val b = board(
            live = listOf(row(status = LiveStatus.OK, recentErrors = null, todayErrors = null)),
            exceptions = ColumnState.DISABLED,
        )
        val page = html(b)
        assertThat(page).contains("사용 안 함")
        assertThat(page).doesNotContain("확인 불가")
    }

    @Test fun 공통_줄과_멈춤_알림을_보여_준다() {
        val page = html(board(common = CommonRow(11, 11, true), staleNotice = "통계 집계가 3시간 넘게 멈춰 있습니다."))
        assertThat(page).contains("공통(고객사 없음)")
        assertThat(page).contains("예외 급증")
        assertThat(page).contains("통계 집계가 3시간 넘게 멈춰 있습니다.")
    }

    @Test fun 기준일이_없으면_판정하지_않는다는_문구를_보여_준다() {
        val page = html(board(baselineDate = null, live = listOf(row(status = LiveStatus.OK, baseline = null))))
        assertThat(page).contains("기준일 없음 — 끊김·감소를 판정하지 않습니다.")
    }

    @Test fun 통계가_없으면_안내_문구를_보여_준다() {
        val page = html(board(week = null))
        assertThat(page).contains("집계된 통계가 없습니다.")
        assertThat(page).contains("어제까지 (통계)")
    }

    @Test fun 하락은_부호와_단위를_붙여_보여_준다() {
        // 두 번째 줄: 인증량 900 → 직전 1,000 대비 -10.0%
        val shrunk = WeekRow(11L, "B은행", WeekTotals(800, 100, 0, 0, 0), prev, false)
        val week = WeekTable(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 23),
            listOf(WeekRow(10L, "A은행", cur, prev, true), shrunk))
        val page = html(board(week = week))
        assertThat(page).contains("91.2%")
        assertThat(page).contains("-6.4%p")
        // 하락 판정(dropped)인 줄만 변화 칸이 위험 배지다
        assertThat(page).contains("<span class=\"badge text-bg-danger\">-6.4%p</span>")
        assertThat(Regex("text-bg-danger\">-").findAll(page).count()).isEqualTo(1)
        assertThat(page).contains("+77")
        assertThat(page).contains("1,000")
        assertThat(page).contains("0.0%")
        assertThat(page).contains("-10.0%")
        assertThat(page).contains("2026-11-17 ~ 2026-11-23, 직전 7일 대비")
    }

    @Test fun 고객사_이름은_이스케이프된다() {
        val b = board(
            live = listOf(row(name = "<b>x</b>")),
            week = WeekTable(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 23),
                listOf(WeekRow(10L, "<b>x</b>", cur, prev, false))),
        )
        val page = html(b)
        assertThat(page).doesNotContain("<b>x</b>")
        assertThat(page).contains("&lt;b&gt;x&lt;/b&gt;")
    }
}
