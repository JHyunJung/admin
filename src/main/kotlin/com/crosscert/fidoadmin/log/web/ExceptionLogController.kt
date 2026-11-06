package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.ReadOnlyController
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.log.entity.CcfaExceptions
import com.crosscert.fidoadmin.log.service.ExceptionLogQueryService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/logs/exceptions")
class ExceptionLogController(
    private val service: ExceptionLogQueryService,
    private val companies: CompanyLookup,
) : ReadOnlyController<CcfaExceptions, Long, ExceptionLogSearchForm>() {

    override fun service(): CrudService<CcfaExceptions, Long, ExceptionLogSearchForm> = service
    override fun basePath(): String = "/logs/exceptions"
    override fun viewDir(): String = "log/exceptions"
    override fun toListView(e: CcfaExceptions): Any = ExceptionLogRow.from(e)
    override fun toDetailView(e: CcfaExceptions): Any = ExceptionLogView.from(e)

    override fun populateDetailModel(e: CcfaExceptions, model: Model) {
        model.addAttribute("companyName", companies.name(e.companyIdx))
    }
}
