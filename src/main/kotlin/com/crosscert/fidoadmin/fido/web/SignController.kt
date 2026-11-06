package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.ReadOnlyController
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.fido.entity.Sign
import com.crosscert.fidoadmin.fido.service.SignQueryService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

/** 서명 조회. 목록은 SignRow(CLOB 제외), 상세는 엔티티 전체(PLAINTEXT 포함). */
@Controller
@RequestMapping("/signs")
class SignController(
    private val service: SignQueryService,
    private val companies: CompanyLookup,
) : ReadOnlyController<Sign, Long, SignSearchForm>() {

    override fun service(): CrudService<Sign, Long, SignSearchForm> = service
    override fun basePath(): String = "/signs"
    override fun viewDir(): String = "fido/sign"
    override fun toListView(entity: Sign): Any = SignRow.of(entity)

    override fun populateDetailModel(entity: Sign, model: Model) {
        model.addAttribute("companyName", companies.name(entity.companyIdx))
    }
}
