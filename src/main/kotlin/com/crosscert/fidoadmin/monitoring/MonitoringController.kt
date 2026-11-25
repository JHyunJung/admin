package com.crosscert.fidoadmin.monitoring

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * SUPER 전용 관제 화면. 접근은 두 겹이다: `SecurityConfig.SUPER_PATHS` 가 URL 에서 먼저 막고,
 * [MonitoringService.board] 가 한 번 더 SUPER 인지 확인한다.
 */
@Controller
class MonitoringController(private val service: MonitoringService) {

    @GetMapping("/monitoring")
    fun index(model: Model): String {
        model.addAttribute("board", service.board())
        model.addAttribute("fmt", MonitoringView)
        return "monitoring/index"
    }
}
