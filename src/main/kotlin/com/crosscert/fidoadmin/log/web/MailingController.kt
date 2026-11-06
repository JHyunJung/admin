package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.ReadOnlyController
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.log.entity.CcfaMailing
import com.crosscert.fidoadmin.log.service.MailingQueryService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

/** 민감 컬럼·CLOB 이 없어 엔티티를 그대로 뷰에 준다(기본 toListView/toDetailView). */
@Controller
@RequestMapping("/logs/mailing")
class MailingController(
    private val service: MailingQueryService,
    private val companies: CompanyLookup,
) : ReadOnlyController<CcfaMailing, Long, MailingSearchForm>() {

    override fun service(): CrudService<CcfaMailing, Long, MailingSearchForm> = service
    override fun basePath(): String = "/logs/mailing"
    override fun viewDir(): String = "log/mailing"

    override fun populateDetailModel(e: CcfaMailing, model: Model) {
        model.addAttribute("companyName", companies.name(e.companyIdx))
    }
}
