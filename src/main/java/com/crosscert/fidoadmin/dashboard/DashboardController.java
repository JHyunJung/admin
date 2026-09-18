package com.crosscert.fidoadmin.dashboard;

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
        if ((search.getGroupby() == null || search.getGroupby().isBlank()) && !groupbys.isEmpty()) {
            search.setGroupby(groupbys.get(0));
        }
        List<DailyStat> daily = search.getGroupby() == null ? List.of() : stats.daily(search);
        model.addAttribute("groupbys", groupbys);
        model.addAttribute("serviceNames", stats.serviceNames());
        model.addAttribute("daily", daily);
        model.addAttribute("totals", StatTotals.of(daily));
        return "dashboard/index";
    }
}
