package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController extends CrudController<CcfaCompany, Long, CompanyForm, CompanySearchForm> {

    private final CompanyService service;

    @Override protected CrudService<CcfaCompany, Long, CompanySearchForm> service() { return service; }
    @Override protected String basePath() { return "/companies"; }
    @Override protected String viewDir() { return "company/company"; }
    @Override protected CompanySearchForm newSearchForm() { return new CompanySearchForm(); }
    @Override protected CompanyForm newForm() { return new CompanyForm(); }
    @Override protected CompanyForm toForm(CcfaCompany e) { return CompanyForm.from(e); }
    @Override protected CcfaCompany toEntity(CompanyForm f) { CcfaCompany c = new CcfaCompany(); f.applyTo(c); return c; }
    @Override protected void applyForm(CompanyForm f, CcfaCompany e) { f.applyTo(e); }
}
