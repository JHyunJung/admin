package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.company.service.FdsPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/fds-policies")
@RequiredArgsConstructor
public class FdsPolicyController extends CrudController<CcfaFdsPolicy, Long, FdsPolicyForm, FdsPolicySearchForm> {

    private final FdsPolicyService service;
    private final CompanyLookup companies;
    private final TenantContext tenant;

    @Override protected CrudService<CcfaFdsPolicy, Long, FdsPolicySearchForm> service() { return service; }
    @Override protected String basePath() { return "/fds-policies"; }
    @Override protected String viewDir() { return "company/fds-policy"; }
    @Override protected FdsPolicySearchForm newSearchForm() { return new FdsPolicySearchForm(); }
    @Override protected FdsPolicyForm newForm() { return new FdsPolicyForm(); }
    @Override protected FdsPolicyForm toForm(CcfaFdsPolicy e) { return FdsPolicyForm.from(e); }

    /**
     * 할당형 PK 이므로 예외적으로 폼의 companyIdx 를 식별자로 채운다.
     * COMPANY 역할은 기반 create() 가 insert() 전에 tenant.companyIdx() 로 덮어쓰고,
     * AssignedIdCrudService.insert() 가 존재 검사 후 persist 하므로 기존 행이 덮어써지지 않는다.
     */
    @Override protected CcfaFdsPolicy toEntity(FdsPolicyForm f) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(f.getCompanyIdx());
        f.applyTo(p);
        return p;
    }
    /** 수정에서는 식별자를 바꾸지 않는다. */
    @Override protected void applyForm(FdsPolicyForm f, CcfaFdsPolicy e) { f.applyTo(e); }

    @Override protected void validate(FdsPolicyForm f, boolean isNew, BindingResult binding) {
        if (isNew && tenant.require().isSuper() && f.getCompanyIdx() == null) {
            binding.rejectValue("companyIdx", "required", "고객사를 선택하세요.");
        }
    }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (tenant.require().isSuper()) model.addAttribute("companies", companies.all());
    }
    @Override protected void populateFormModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (tenant.require().isSuper()) model.addAttribute("companies", companies.all());
    }
    @Override protected void populateDetailModel(CcfaFdsPolicy e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
