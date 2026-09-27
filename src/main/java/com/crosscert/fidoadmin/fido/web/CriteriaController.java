package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * AAID(정책) 조회와 고객사별 활성/비활성 토글.
 *
 * <p>기준 데이터(CRITERIA) 자체는 전역이지만 토글 상태는 고객사별이라
 * COMPANY 역할도 쓴다. 고객사 경계는 서비스가 유효 테넌트로 건다.
 */
@Controller
@RequestMapping("/criteria")
@RequiredArgsConstructor
public class CriteriaController extends ReadOnlyController<Criteria, Long, CriteriaSearchForm> {

    private final CriteriaQueryService service;

    @Override protected CrudService<Criteria, Long, CriteriaSearchForm> service() { return service; }
    @Override protected String basePath() { return "/criteria"; }
    @Override protected String viewDir() { return "fido/criteria"; }
    @Override protected Object toListView(Criteria entity) { return CriteriaRow.of(entity); }
    @Override protected Object toDetailView(Criteria entity) { return CriteriaView.of(entity); }

    /** 목록의 상태 뱃지가 쓴다. 현재 고객사에서 꺼 둔 AAID 집합. */
    @Override
    protected void populateListModel(Model model) {
        model.addAttribute("disabledAaids", service.disabledAaids());
    }

    /**
     * 고객사별 AAID 활성/비활성 토글.
     *
     * <p>검색·페이지 상태를 리다이렉트에 다시 실어 준다. 토글 후 1페이지로
     * 튕기면 목록을 훑으며 여러 건을 끄고 켤 때 매번 찾아 들어가야 한다.
     */
    @PostMapping("/{id}/status")
    public String changeStatus(
            @PathVariable("id") Long id,
            @RequestParam("enabled") boolean enabled,
            @ModelAttribute("search") CriteriaSearchForm search,
            RedirectAttributes redirect) {
        boolean changed = service.changeStatus(id, enabled);
        redirect.addFlashAttribute("statusMessage",
            changed ? "상태를 변경했습니다." : "이미 해당 상태입니다.");
        redirect.addAttribute("aaid", search.getAaid());
        redirect.addAttribute("page", search.getPage());
        redirect.addAttribute("size", search.getSize());
        return "redirect:/criteria";
    }
}
