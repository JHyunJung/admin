package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.audit.AuditView
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.log.service.FidoLogQueryService
import java.time.LocalDate
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

/**
 * FIDO 로그 화면.
 *
 * `ReadOnlyController` 를 상속하지 않는다. 그 기반은 `CrudService`(고정 테이블
 * JPA)를 전제하는데, FIDO 로그는 날짜별로 테이블이 갈려 조회 시점에 대상이 정해진다.
 * 상세 경로에 날짜가 들어가는 것도 그래서다 — `/logs/fido/2026-09-26/1` 처럼
 * **어느 테이블의** 몇 번인지가 있어야 한 건을 가리킬 수 있다.
 */
@Controller
@RequestMapping("/logs/fido")
class FidoLogController(
    private val service: FidoLogQueryService,
    private val companies: CompanyLookup,
) {

    /** 목록. 날짜를 고르지 않으면 폼 기본값(오늘)로 조회한다. */
    @GetMapping
    fun list(@ModelAttribute("search") search: FidoLogSearchForm, model: Model): String {
        // 정렬은 IDX DESC 고정이다(서비스의 SQL). 정렬 파라미터를 받지 않으므로
        // 사용자 입력이 ORDER BY 로 들어갈 경로가 없다.
        val result = service.search(search, search.toPageable(Sort.unsorted()))
        model.addAttribute("page", result.page)
        model.addAttribute("truncated", result.truncated)
        model.addAttribute("searchQs", search.toQueryString())
        model.addAttribute("basePath", "/logs/fido")
        return "log/fido/list"
    }

    /**
     * 상세. 날짜가 곧 테이블이라 경로에 함께 들어간다.
     *
     * `AuditView.add` 로 조회 사실을 남긴다 — 인증 로그는 개인정보가 섞이므로
     * 누가 어느 건을 열어 봤는지가 감사 대상이다.
     */
    @GetMapping("/{date}/{id}")
    fun detail(
        @PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @PathVariable("id") id: Long,
        model: Model,
    ): String {
        val item = service.get(date, id)
        model.addAttribute("item", item)
        model.addAttribute("logDate", date)
        model.addAttribute("basePath", "/logs/fido")
        model.addAttribute("companyName", companies.name(item.companyIdx))
        AuditView.add(model, "FIDO_LOGS", item.idx.toString())
        return "log/fido/detail"
    }
}
