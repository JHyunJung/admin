package com.crosscert.fidoadmin.dashboard

import java.time.LocalDate
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute

@Controller
class DashboardController(private val stats: StatisticsQueryService) {

    @GetMapping("/")
    fun index(@ModelAttribute("search") search: DashboardSearchForm, model: Model): String {
        val daily = stats.daily(search)
        val range = StatisticsQueryService.rangeOf(search)
        model.addAttribute("serviceNames", stats.serviceNames())
        model.addAttribute("daily", daily)
        model.addAttribute("totals", StatTotals.of(daily))
        model.addAttribute("range", range)
        // 그래프가 비거나 끝이 잘렸을 때 이유를 알려 주기 위한 값. 이 조건으로 집계된 적이 없으면 null.
        val last: LocalDate? = stats.lastStatDate(search).orElse(null)
        model.addAttribute("lastStatDate", last)
        model.addAttribute("noStatsAtAll", last == null)
        model.addAttribute("staleAfterLast", last != null && daily.isNotEmpty() && last.isBefore(range.to))
        return "dashboard/index"
    }
}
