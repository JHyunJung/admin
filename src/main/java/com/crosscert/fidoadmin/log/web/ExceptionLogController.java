package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.log.entity.CcfaExceptions;
import com.crosscert.fidoadmin.log.service.ExceptionLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/logs/exceptions")
@RequiredArgsConstructor
public class ExceptionLogController extends ReadOnlyController<CcfaExceptions, Long, ExceptionLogSearchForm> {

    private final ExceptionLogQueryService service;
    private final CompanyLookup companies;
    private final TenantContext tenant;

    @Override protected CrudService<CcfaExceptions, Long, ExceptionLogSearchForm> service() { return service; }
    @Override protected String basePath() { return "/logs/exceptions"; }
    @Override protected String viewDir() { return "log/exceptions"; }
    @Override protected Object toListView(CcfaExceptions e) { return ExceptionLogRow.from(e); }
    @Override protected Object toDetailView(CcfaExceptions e) { return ExceptionLogView.from(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (tenant.require().isSuper()) model.addAttribute("companies", companies.all());
    }

    @Override protected void populateDetailModel(CcfaExceptions e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
