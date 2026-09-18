package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.company.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/licenses")
@RequiredArgsConstructor
public class LicenseController extends CrudController<CcfaLicense, Long, LicenseForm, LicenseSearchForm> {

    private final LicenseService service;
    private final CompanyLookup companies;

    @Override protected CrudService<CcfaLicense, Long, LicenseSearchForm> service() { return service; }
    @Override protected String basePath() { return "/licenses"; }
    @Override protected String viewDir() { return "company/license"; }
    @Override protected LicenseSearchForm newSearchForm() { return new LicenseSearchForm(); }
    @Override protected LicenseForm newForm() { return new LicenseForm(); }
    @Override protected LicenseForm toForm(CcfaLicense e) { return LicenseForm.from(e); }
    @Override protected CcfaLicense toEntity(LicenseForm f) { CcfaLicense l = new CcfaLicense(); f.applyTo(l); return l; }
    @Override protected void applyForm(LicenseForm f, CcfaLicense e) { f.applyTo(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
    }
    @Override protected void populateDetailModel(CcfaLicense e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
