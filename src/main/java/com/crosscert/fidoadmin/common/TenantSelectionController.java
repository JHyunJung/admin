package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 고객사 선택. 슈퍼관리자가 로그인 직후 처음 만나는 화면이다.
 *
 * <p>고객사 존재 검사에 CompanyLookup 이 아니라 리포지터리를 직접 쓴다.
 * CompanyLookup 은 로그인 사용자 기준으로 결과를 가리는 화면용 조회라
 * 없는 고객사와 볼 수 없는 고객사를 구분하지 못한다
 * (SignupService.approve() 가 같은 이유로 리포지터리를 쓴다).
 */
@Controller
@RequiredArgsConstructor
public class TenantSelectionController {

    private final SelectedTenant selected;
    private final TenantContext tenant;
    private final CompanyLookup companies;
    private final CcfaCompanyRepository repository;
    private final MenuRegistry menus;

    @GetMapping("/select-tenant")
    public String selectForm(Model model) {
        if (!tenant.require().isSuper()) return "redirect:/";
        model.addAttribute("companies", companies.all().stream()
            .filter(c -> c.getIdx() != null && c.getIdx() != 0L)
            .toList());
        model.addAttribute("systemMenus", menus.itemsFor(true).stream()
            .filter(m -> m.area() == MenuArea.SYSTEM)
            .toList());
        return "tenant/select";
    }

    @PostMapping("/select-tenant")
    public String select(@RequestParam(required = false) Long companyIdx,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes redirect) {
        if (!tenant.require().isSuper()) return "redirect:/";
        if (companyIdx == null || companyIdx == 0L || !repository.existsById(companyIdx)) {
            redirect.addFlashAttribute("flashError", "선택할 수 없는 고객사입니다.");
            return "redirect:/select-tenant";
        }
        selected.select(companyIdx);
        return "redirect:" + safeReturnTo(returnTo);
    }

    /**
     * 돌아갈 경로를 정한다.
     *
     * <p>슬래시 하나로 시작하는 내부 경로만 받는다("//host" 와 "https://host" 는 외부다).
     * 상세·수정 경로(/users/123)는 그 ID 가 이전 테넌트의 것이라 새 테넌트에서 404 가
     * 되므로 메뉴에 등록된 목록 경로로 절상한다.
     */
    private String safeReturnTo(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//")) return "/";
        if (menus.areaOf(returnTo) != MenuArea.TENANT) return "/";
        return menus.listPathFor(returnTo);
    }
}
