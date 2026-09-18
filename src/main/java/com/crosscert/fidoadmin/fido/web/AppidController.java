package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Appid;
import com.crosscert.fidoadmin.fido.service.AppidService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/appids")
@RequiredArgsConstructor
public class AppidController extends CrudController<Appid, Long, AppidForm, AppidSearchForm> {

    private final AppidService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Appid, Long, AppidSearchForm> service() { return service; }
    @Override protected String basePath() { return "/appids"; }
    @Override protected String viewDir() { return "fido/appid"; }
    @Override protected AppidSearchForm newSearchForm() { return new AppidSearchForm(); }
    @Override protected AppidForm newForm() { return new AppidForm(); }
    @Override protected AppidForm toForm(Appid e) { return AppidForm.from(e); }
    @Override protected Appid toEntity(AppidForm f) { Appid a = new Appid(); f.applyTo(a); return a; }
    @Override protected void applyForm(AppidForm f, Appid e) { f.applyTo(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
    }
    @Override protected void populateDetailModel(Appid e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
