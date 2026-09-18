package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import com.crosscert.fidoadmin.log.service.AuditLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/logs/audit")
@RequiredArgsConstructor
public class AuditLogController extends ReadOnlyController<CcfaAuditLog, Long, AuditLogSearchForm> {

    private final AuditLogQueryService service;

    @Override protected CrudService<CcfaAuditLog, Long, AuditLogSearchForm> service() { return service; }
    @Override protected String basePath() { return "/logs/audit"; }
    @Override protected String viewDir() { return "log/audit"; }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("types", AuditType.values());
    }
}
