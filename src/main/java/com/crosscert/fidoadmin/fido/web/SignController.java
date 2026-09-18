package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Sign;
import com.crosscert.fidoadmin.fido.service.SignQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** 서명 조회. 목록은 SignRow(CLOB 제외), 상세는 엔티티 전체(PLAINTEXT 포함). */
@Controller
@RequestMapping("/signs")
@RequiredArgsConstructor
public class SignController extends ReadOnlyController<Sign, Long, SignSearchForm> {

    private final SignQueryService service;
    private final CompanyLookup companies;
    private final TenantContext tenant;

    @Override protected CrudService<Sign, Long, SignSearchForm> service() { return service; }
    @Override protected String basePath() { return "/signs"; }
    @Override protected String viewDir() { return "fido/sign"; }
    @Override protected Object toListView(Sign entity) { return SignRow.of(entity); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (tenant.require().isSuper()) model.addAttribute("companies", companies.all());
    }

    @Override protected void populateDetailModel(Sign entity, Model model) {
        model.addAttribute("companyName", companies.name(entity.getCompanyIdx()));
    }
}
