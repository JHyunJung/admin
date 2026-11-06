package com.crosscert.fidoadmin.company.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.company.entity.CcfaLicense
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.company.service.LicenseService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/licenses")
class LicenseController(
    private val service: LicenseService,
    private val companies: CompanyLookup,
) : CrudController<CcfaLicense, Long, LicenseForm, LicenseSearchForm>() {

    override fun service(): CrudService<CcfaLicense, Long, LicenseSearchForm> = service
    override fun basePath(): String = "/licenses"
    override fun viewDir(): String = "company/license"
    override fun newForm(): LicenseForm = LicenseForm()
    override fun toForm(e: CcfaLicense): LicenseForm = LicenseForm.from(e)
    override fun toEntity(f: LicenseForm): CcfaLicense { val l = CcfaLicense(); f.applyTo(l); return l }
    override fun applyForm(f: LicenseForm, e: CcfaLicense) { f.applyTo(e) }

    override fun populateDetailModel(e: CcfaLicense, model: Model) {
        model.addAttribute("companyName", companies.name(e.companyIdx))
    }
}
