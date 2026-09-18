package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.log.service.MailingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** 민감 컬럼·CLOB 이 없어 엔티티를 그대로 뷰에 준다(기본 toListView/toDetailView). */
@Controller
@RequestMapping("/logs/mailing")
@RequiredArgsConstructor
public class MailingController extends ReadOnlyController<CcfaMailing, Long, MailingSearchForm> {

    private final MailingQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<CcfaMailing, Long, MailingSearchForm> service() { return service; }
    @Override protected String basePath() { return "/logs/mailing"; }
    @Override protected String viewDir() { return "log/mailing"; }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
    }

    @Override protected void populateDetailModel(CcfaMailing e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
