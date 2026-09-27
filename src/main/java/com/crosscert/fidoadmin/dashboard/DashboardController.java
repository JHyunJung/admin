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
        List<String> groupbys = stats.groupbys();
        search.setGroupby(resolveGroupby(search.getGroupby(), groupbys));
        List<DailyStat> daily = search.getGroupby() == null ? List.of() : stats.daily(search);
        StatisticsQueryService.Range range = StatisticsQueryService.rangeOf(search);
        model.addAttribute("groupbys", groupbys);
        model.addAttribute("serviceNames", stats.serviceNames());
        model.addAttribute("daily", daily);
        model.addAttribute("totals", StatTotals.of(daily));
        model.addAttribute("range", range);
        // 그래프가 비거나 끝이 잘렸을 때 이유를 알려 주기 위한 값. 통계가 아예 없는 고객사면 조회하지 않는다.
        LocalDate last = groupbys.isEmpty() || search.getGroupby() == null
            ? null : stats.lastStatDate(search).orElse(null);
        model.addAttribute("lastStatDate", last);
        model.addAttribute("noStatsAtAll", groupbys.isEmpty());
        model.addAttribute("staleAfterLast", last != null && !daily.isEmpty() && last.isBefore(range.to()));
        return "dashboard/index";
    }

    /**
     * 요청한 집계 단위를 목록의 표기로 맞춘다. 대소문자만 다르면(DAY/day) 목록 값을 쓰고,
     * 비었거나 목록에 없으면 첫 값을 쓴다. 목록이 비면(통계 없는 고객사) null.
     */
    static String resolveGroupby(String requested, List<String> groupbys) {
        if (groupbys.isEmpty()) return null;
        if (requested != null && !requested.isBlank()) {
            for (String g : groupbys) if (g != null && g.equalsIgnoreCase(requested.trim())) return g;
        }
        return groupbys.get(0);
    }
}
