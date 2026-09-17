package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.log.service.FidoLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/logs/fido")
@RequiredArgsConstructor
public class FidoLogController extends ReadOnlyController<FidoLogs, Long, FidoLogSearchForm> {

    private final FidoLogQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<FidoLogs, Long, FidoLogSearchForm> service() { return service; }
    @Override protected String basePath() { return "/logs/fido"; }
    @Override protected String viewDir() { return "log/fido"; }
    @Override protected Object toListView(FidoLogs e) { return FidoLogRow.from(e); }
    @Override protected Object toDetailView(FidoLogs e) { return FidoLogView.from(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
        if (TenantContext.isSuper()) model.addAttribute("companies", companies.all());
    }

    @Override protected void populateDetailModel(FidoLogs e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
