package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.PasswordPolicy;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.service.ManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/managers")
@RequiredArgsConstructor
public class ManagerController extends CrudController<CcfaManager, Long, ManagerForm, ManagerSearchForm> {

    private final ManagerService service;
    private final CompanyLookup companies;
    private final PasswordEncoder encoder;

    @Override protected CrudService<CcfaManager, Long, ManagerSearchForm> service() { return service; }
    @Override protected String basePath() { return "/managers"; }
    @Override protected String viewDir() { return "manager/manager"; }
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
        // 수정 시 비밀번호를 비워두면 변경하지 않는다는 뜻이므로 정책 검사를 건너뛴다.
        if (f.hasPassword()) {
            PasswordPolicy.validatePassword(f.getPassword(), f.getPasswordConfirm(), binding);
        }
    }

    @Override protected Object toListView(CcfaManager e) { return ManagerRow.of(e); }
    @Override protected Object toDetailView(CcfaManager e) { return ManagerView.of(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        model.addAttribute("companies", companies.all());
    }
    @Override protected void populateFormModel(Model model) { model.addAttribute("companies", companies.all()); }
    @Override protected void populateDetailModel(CcfaManager e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
        model.addAttribute("lockState", service.lockState(e.getUserId()).orElse(null));
    }

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id, RedirectAttributes redirect) {
        service.unlock(id);
        redirect.addFlashAttribute("flashSuccess", "잠금이 해제되었습니다.");
        return "redirect:/managers/" + id;
    }
}
