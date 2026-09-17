package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.service.AppserverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/appservers")
@RequiredArgsConstructor
public class AppserverController extends CrudController<Appserver, Long, AppserverForm, AppserverSearchForm> {

    private final AppserverService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Appserver, Long, AppserverSearchForm> service() { return service; }
    @Override protected String basePath() { return "/appservers"; }
    @Override protected String viewDir() { return "fido/appserver"; }
    @Override protected AppserverSearchForm newSearchForm() { return new AppserverSearchForm(); }
    @Override protected AppserverForm newForm() { return new AppserverForm(); }
    @Override protected AppserverForm toForm(Appserver e) { return AppserverForm.from(e); }
    @Override protected Appserver toEntity(AppserverForm f) { Appserver a = new Appserver(); f.applyTo(a); return a; }
    @Override protected void applyForm(AppserverForm f, Appserver e) { f.applyTo(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
    @Override protected void populateDetailModel(Appserver e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
    @Override protected void populateFormModel(Model model) {
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }
}
