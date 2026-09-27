package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.company.service.FdsMonitorQueryService;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * FDS 모니터링 — 같은 기기의 N초 이내 반복 요청 목록. 조회 전용이다.
 *
 * <p>{@code ReadOnlyController} 를 상속하지 않는다. 그 기반은 {@code CrudService}(고정 테이블
 * JPA)를 전제하는데 이 화면은 날짜별 분할 테이블을 JDBC 로 읽는다({@code FidoLogController} 와 같다).
 */
@Controller
@RequestMapping("/fds-monitor")
@RequiredArgsConstructor
public class FdsMonitorController {

    private final FdsMonitorQueryService service;

    /**
     * 정렬은 서비스가 고정한다({@code Sort.unsorted()} 를 넘겨 정렬 파라미터를 무시한다).
     *
     * <p>{@code BindingResult} 를 폼 바로 뒤에 두어야 한다. 없으면 {@code term=abc} 같은
     * 타입 불일치가 400 으로 끝나 운영자가 무엇을 잘못 넣었는지 볼 수 없다.
     */
    @GetMapping
    public String list(@Valid @ModelAttribute("search") FdsMonitorSearchForm search, BindingResult binding, Model model) {
        Optional<Integer> policyTerm = service.policyTerm();
        Integer effective = search.getTerm() != null ? search.getTerm() : policyTerm.orElse(null);

        model.addAttribute("policyTerm", policyTerm.orElse(null));
        model.addAttribute("effectiveTerm", effective);
        model.addAttribute("basePath", "/fds-monitor");
        model.addAttribute("searchQs", search.toQueryString());

        // 폼 오류(term 이 숫자가 아니거나 범위 밖)나 기준 없음이면 조회하지 않는다.
        // 기준 없이 조회하면 "오늘은 이상 없음"처럼 보여 정책이 빠진 것을 가린다.
        if (binding.hasErrors() || effective == null) {
            model.addAttribute("page", Page.empty());
            return "company/fds-monitor/list";
        }

        model.addAttribute("page", service.search(search.getLogDate(), search.getServicename(), effective,
            search.toPageable(Sort.unsorted())));
        return "company/fds-monitor/list";
    }
}
