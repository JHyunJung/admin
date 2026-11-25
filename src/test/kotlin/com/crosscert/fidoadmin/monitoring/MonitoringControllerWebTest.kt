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
        recentLogs: ColumnState = logs,
        baselineLogs: ColumnState = logs,
        exceptions: ColumnState = ColumnState.OK,
        baselineDate: LocalDate? = LocalDate.of(2026, 11, 24),
        common: CommonRow? = null,
        staleNotice: String? = null,
        week: WeekTable? = WeekTable(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 23),
            listOf(WeekRow(10L, "A은행", cur, prev, true))),
    ) = MonitoringBoard(
        asOf = asOf, cacheSeconds = 60, logs = logs, recentLogs = recentLogs, baselineLogs = baselineLogs,
        exceptions = exceptions, baselineDate = baselineDate, common = common, live = live,
        staleNotice = staleNotice, week = week,
    )

    /** "지금" 표에서 첫 칸이 [name] 인 줄의 HTML. 주간 표에도 같은 이름이 있어 "지금" 구획만 본다. */
    private fun rowHtml(page: String, name: String): String =
        Regex("<tr>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
            .findAll(page.substringAfter("id=\"live-title\"").substringBefore("id=\"week-title\""))
            .map { it.groupValues[1] }
            .single { it.contains(Regex("<td>\\s*" + Regex.escape(name) + "\\s*</td>")) }

    /** 첫 칸이 [name] 인 줄의 칸 글자들. 태그를 걷어 내고 앞뒤 공백을 뺀다. */
    private fun cells(page: String, name: String): List<String> =
        Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(rowHtml(page, name))
            .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").trim() }.toList()

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

    @Test fun 로그_조회가_실패하면_로그_칸이_모두_확인_불가다() {
        // 상태를 예외 급증으로 두어 상태 배지의 "확인 불가"와 섞이지 않게 한다
        val b = board(
            live = listOf(row(status = LiveStatus.EXCEPTION_SPIKE, recent = null, baseline = null, today = null, last = null,
                recentErrors = 12, todayErrors = 30)),
            logs = ColumnState.FAILED, baselineDate = null,
        )
        val page = html(b)
        assertThat(cells(page, "A은행"))
            .containsExactly("A은행", "예외 급증", "확인 불가", "확인 불가", "확인 불가", "확인 불가", "12", "30")
        assertThat(page).contains("로그 확인 불가 — 끊김·감소를 판정하지 않습니다.")
        assertThat(page).doesNotContain("기준일 없음")
        assertThat(page).doesNotContain("끊김<")
    }

    @Test fun 로그_조회가_실패하고_상태를_모르면_확인_불가_배지다() {
        val b = board(
            live = listOf(row(status = LiveStatus.UNKNOWN, recent = null, baseline = null, today = null, last = null)),
            logs = ColumnState.FAILED, baselineDate = null,
        )
        val row = rowHtml(html(b), "A은행")
        assertThat(row).contains("<span class=\"badge text-bg-secondary\">확인 불가</span>")
    }

    @Test fun 로그_조회를_끄면_로그_칸과_상태가_사용_안_함이고_확인_불가는_없다() {
        val b = board(
            live = listOf(row(status = LiveStatus.UNKNOWN, recent = null, baseline = null, today = null, last = null)),
            logs = ColumnState.DISABLED, baselineDate = null,
        )
        val page = html(b)
        assertThat(cells(page, "A은행"))
            .containsExactly("A은행", "사용 안 함", "사용 안 함", "사용 안 함", "사용 안 함", "사용 안 함", "1", "2")
        assertThat(rowHtml(page, "A은행")).doesNotContain("badge")
        assertThat(page).contains("로그 조회 사용 안 함 — 끊김·감소를 판정하지 않습니다.")
        assertThat(page).doesNotContain("기준일 없음")
        assertThat(page).doesNotContain("확인 불가")
    }

    @Test fun 예외_조회가_실패해도_공통_줄은_남고_예외_칸은_확인_불가다() {
        val b = board(
            live = listOf(row(status = LiveStatus.OK, recentErrors = null, todayErrors = null)),
            exceptions = ColumnState.FAILED, common = null,
        )
        val page = html(b)
        assertThat(cells(page, "공통(고객사 없음)"))
            .containsExactly("공통(고객사 없음)", "-", "-", "-", "-", "-", "확인 불가", "확인 불가")
        assertThat(cells(page, "A은행"))
            .containsExactly("A은행", "정상", "0", "412", "1,500", "13:02:00", "확인 불가", "확인 불가")
    }

    @Test fun 예외_조회를_끄면_공통_줄은_남고_예외_칸은_사용_안_함이다() {
        val b = board(
            live = listOf(row(status = LiveStatus.OK, recentErrors = null, todayErrors = null)),
            exceptions = ColumnState.DISABLED, common = null,
        )
        val page = html(b)
        assertThat(cells(page, "공통(고객사 없음)"))
            .containsExactly("공통(고객사 없음)", "-", "-", "-", "-", "-", "사용 안 함", "사용 안 함")
        assertThat(cells(page, "A은행").takeLast(2)).containsExactly("사용 안 함", "사용 안 함")
        assertThat(page).doesNotContain("확인 불가")
    }

    @Test fun 자정의_전날_꼬리를_못_읽으면_최근과_기준_칸은_확인_불가다() {
        val b = board(
            live = listOf(row(status = LiveStatus.UNKNOWN, recent = null, baseline = null, today = 53)),
            logs = ColumnState.OK, recentLogs = ColumnState.FAILED, baselineLogs = ColumnState.FAILED, baselineDate = null,
        )
        val page = html(b)
        assertThat(cells(page, "A은행"))
            .containsExactly("A은행", "확인 불가", "확인 불가", "확인 불가", "53", "13:02:00", "1", "2")
        assertThat(page).contains("기준일 확인 불가 — 끊김·감소를 판정하지 않습니다.")
        assertThat(page).doesNotContain("기준일 없음")
    }

    @Test fun 로그가_없는_고객사의_상태는_배지_없이_대시다() {
        val b = board(live = listOf(row(status = LiveStatus.NONE, recent = 0, baseline = 0, today = 0, last = null,
            recentErrors = 0, todayErrors = 0)))
        val page = html(b)
        assertThat(cells(page, "A은행")).containsExactly("A은행", "-", "0", "0", "0", "-", "0", "0")
        assertThat(rowHtml(page, "A은행")).doesNotContain("badge")
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
        assertThat(cells(page, "A은행")[3]).isEqualTo("-")
    }

    @Test fun 통계가_없으면_안내는_위에_한_번이고_아래_표는_표시할_통계가_없다고_한다() {
        // 빈 DB: 통계도 당일 테이블도 없다
        val b = board(
            live = listOf(row(status = LiveStatus.NONE, recent = 0, baseline = null, today = 0, last = null,
                recentErrors = 0, todayErrors = 0)),
            baselineDate = null, common = CommonRow(0, 0, false), staleNotice = "집계된 통계가 없습니다.", week = null,
        )
        val page = html(b)
        assertThat(Regex("집계된 통계가 없습니다.").findAll(page).count()).isEqualTo(1)
        assertThat(page).contains("표시할 통계가 없습니다.")
        assertThat(page).contains("최근 7일 (통계)")
        assertThat(cells(page, "A은행")).containsExactly("A은행", "-", "0", "-", "0", "-", "0", "0")
        assertThat(cells(page, "공통(고객사 없음)")).containsExactly("공통(고객사 없음)", "-", "-", "-", "-", "-", "0", "0")
        assertThat(page).contains("기준일 없음 — 끊김·감소를 판정하지 않습니다.")
    }

    @Test fun 구획_제목은_표_카드의_머리에_있다() {
        val page = html(board())
        assertThat(page).containsPattern("<div class=\"fa-section-head\">\\s*<h2 id=\"live-title\" class=\"fa-section-title\">")
        assertThat(page).containsPattern("<div class=\"fa-section-head\">\\s*<h2 id=\"week-title\" class=\"fa-section-title\">")
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
