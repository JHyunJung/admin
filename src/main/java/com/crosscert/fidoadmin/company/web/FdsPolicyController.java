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
     * 할당형 PK 이므로 폼이 아니라 유효 테넌트로 식별자를 채운다. 고객사 select 가
     * 사라졌으므로 폼에는 값이 없다. AssignedIdCrudService.insert() 가 존재 검사 후
     * persist 하므로 이미 정책이 있는 고객사를 고른 채 등록하면 기존과 같은 중복 거부가 난다.
     */
    @Override protected CcfaFdsPolicy toEntity(FdsPolicyForm f) {
        CcfaFdsPolicy p = new CcfaFdsPolicy();
        p.setCompanyIdx(tenant.companyIdx());
        f.applyTo(p);
        return p;
    }
    /** 수정에서는 식별자를 바꾸지 않는다. */
    @Override protected void applyForm(FdsPolicyForm f, CcfaFdsPolicy e) { f.applyTo(e); }

    @Override protected void populateFormModel(Model model) {
        model.addAttribute("companyNames", companies.names());
    }
    @Override protected void populateDetailModel(CcfaFdsPolicy e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
