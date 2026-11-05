package com.crosscert.fidoadmin.dashboard;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final StatisticsQueryService stats;

    @GetMapping("/")
    public String index(@ModelAttribute("search") DashboardSearchForm search, Model model) {
        List<DailyStat> daily = stats.daily(search);
        StatisticsQueryService.Range range = StatisticsQueryService.rangeOf(search);
        model.addAttribute("serviceNames", stats.serviceNames());
        model.addAttribute("daily", daily);
        model.addAttribute("totals", StatTotals.of(daily));
        model.addAttribute("range", range);
        // 그래프가 비거나 끝이 잘렸을 때 이유를 알려 주기 위한 값. 이 조건으로 집계된 적이 없으면 null.
        LocalDate last = stats.lastStatDate(search).orElse(null);
        model.addAttribute("lastStatDate", last);
        model.addAttribute("noStatsAtAll", last == null);
        model.addAttribute("staleAfterLast", last != null && !daily.isEmpty() && last.isBefore(range.to()));
        return "dashboard/index";
    }
}
