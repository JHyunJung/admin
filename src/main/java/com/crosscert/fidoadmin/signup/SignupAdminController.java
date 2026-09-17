package com.crosscert.fidoadmin.signup;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 가입 승인. SUPER 전용이며 접근 차단은 {@code SecurityConfig} 가 한다(설계서 6.2). */
@Controller
@RequestMapping("/signups")
@RequiredArgsConstructor
public class SignupAdminController {

    private final SignupService service;
    private final CompanyLookup companies;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", service.pending());
        // 전역(IDX 0)은 선택 목록에서 제외한다. 승인으로 슈퍼 관리자를 만들 수 없게 하기 위해서다.
        // SignupService.approve() 의 거부가 최후 방어선이고, 여기서는 애초에 고를 수 없게 한다.
        List<CcfaCompany> selectable = companies.all().stream()
            .filter(c -> c.getIdx() != null && c.getIdx() != SignupPolicy.SUPER_COMPANY_IDX)
            .toList();
        model.addAttribute("companies", selectable);
        return "signup/list";
    }

    /**
     * 승인. 검증은 전부 서비스에 있고 컨트롤러는 그 거부를 화면 메시지로 옮기기만 한다.
     *
     * <p>{@code companyIdx} 를 {@code required = false} 로 받는 이유: select 를 비운 채 보내면
     * 빈 문자열이 오고, 필수 파라미터로 받으면 Spring 이 400 을 돌려준다. 운영자에게는
     * 흰 오류 화면 대신 "고객사를 선택해야 합니다" 가 보여야 하므로 null 로 받아 서비스에 넘긴다.
     */
    @PostMapping("/{idx}/approve")
    public String approve(@PathVariable Long idx,
                          @RequestParam(required = false) Long companyIdx,
                          RedirectAttributes redirect) {
        try {
            service.approve(idx, companyIdx);
            redirect.addFlashAttribute("flashSuccess", "가입을 승인했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 입력 거부(IllegalArgumentException)와 상태 거부(IllegalStateException)는
            // 둘 다 운영자가 고칠 수 있는 상황이다. 500 으로 새지 않게 여기서 잡는다.
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/signups";
    }

    @PostMapping("/{idx}/reject")
    public String reject(@PathVariable Long idx,
                         @RequestParam(required = false) String reason,
                         RedirectAttributes redirect) {
        try {
            service.reject(idx, reason);
            redirect.addFlashAttribute("flashSuccess", "가입을 거절했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/signups";
    }
}
