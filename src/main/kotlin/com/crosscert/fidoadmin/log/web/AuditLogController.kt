package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.ReadOnlyController
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog
import com.crosscert.fidoadmin.log.service.AuditLogQueryService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/logs/audit")
class AuditLogController(private val service: AuditLogQueryService) :
    ReadOnlyController<CcfaAuditLog, Long, AuditLogSearchForm>() {

    override fun service(): CrudService<CcfaAuditLog, Long, AuditLogSearchForm> = service
    override fun basePath(): String = "/logs/audit"
    override fun viewDir(): String = "log/audit"

    override fun populateListModel(model: Model) {
        model.addAttribute("types", AuditType.values())
    }
}
