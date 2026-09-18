package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.PasswordPolicy;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.service.SuperManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * /managers/super — 슈퍼관리자 계정(COMPANY_IDX = 0) 전용 화면. SYSTEM 영역이라
 * 테넌트 선택 여부와 무관하게 열린다(MenuRegistry). ManagerController 와 URL 이
 * 겹치지 않는다: "/managers/{id}" 의 {id} 는 Long 이라 "super" 는 변환에 실패해
 * ManagerController 로 넘어가지 않고, 더 구체적인 이 매핑이 먼저 선택된다.
 */
@Controller
@RequestMapping("/managers/super")
@RequiredArgsConstructor
public class SuperManagerController extends CrudController<CcfaManager, Long, ManagerForm, ManagerSearchForm> {

    private final SuperManagerService service;
    private final PasswordEncoder encoder;

    @Override protected CrudService<CcfaManager, Long, ManagerSearchForm> service() { return service; }
    @Override protected String basePath() { return "/managers/super"; }
    @Override protected String viewDir() { return "manager/super"; }
    @Override protected ManagerSearchForm newSearchForm() { return new ManagerSearchForm(); }
    @Override protected ManagerForm newForm() { return new ManagerForm(); }
    @Override protected ManagerForm toForm(CcfaManager e) { return ManagerForm.from(e); }

    @Override protected CcfaManager toEntity(ManagerForm f) {
        CcfaManager m = new CcfaManager();
        f.applyTo(m);
        m.setUserPw(encoder.encode(f.getPassword()));
        return m;
    }
    /** 수정: 비밀번호를 입력했을 때만 바꾼다. */
    @Override protected void applyForm(ManagerForm f, CcfaManager e) {
        f.applyTo(e);
        if (f.hasPassword()) e.setUserPw(encoder.encode(f.getPassword()));
    }

    @Override protected void validate(ManagerForm f, boolean isNew, BindingResult binding) {
        if (isNew && !f.hasPassword()) {
            binding.rejectValue("password", "required", "비밀번호는 필수입니다.");
        }
        if (f.hasPassword()) {
            PasswordPolicy.validatePassword(f.getPassword(), f.getPasswordConfirm(), binding);
        }
    }

    @Override protected Object toListView(CcfaManager e) { return ManagerRow.of(e); }
    @Override protected Object toDetailView(CcfaManager e) { return ManagerView.of(e); }

    @Override protected void populateDetailModel(CcfaManager e, Model model) {
        model.addAttribute("lockState", service.lockState(e.getUserId()).orElse(null));
    }

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id, RedirectAttributes redirect) {
        service.unlock(id);
        redirect.addFlashAttribute("flashSuccess", "잠금이 해제되었습니다.");
        return "redirect:/managers/super/" + id;
    }
}
