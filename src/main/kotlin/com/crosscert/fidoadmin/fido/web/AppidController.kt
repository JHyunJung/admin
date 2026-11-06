package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.fido.entity.Appid
import com.crosscert.fidoadmin.fido.service.AppidService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/appids")
class AppidController(
    private val service: AppidService,
    private val companies: CompanyLookup,
) : CrudController<Appid, Long, AppidForm, AppidSearchForm>() {

    override fun service(): CrudService<Appid, Long, AppidSearchForm> = service
    override fun basePath(): String = "/appids"
    override fun viewDir(): String = "fido/appid"
    override fun newForm(): AppidForm = AppidForm()
    override fun toForm(e: Appid): AppidForm = AppidForm.from(e)
    override fun toEntity(f: AppidForm): Appid { val a = Appid(); f.applyTo(a); return a }
    override fun applyForm(f: AppidForm, e: Appid) { f.applyTo(e) }

    override fun populateDetailModel(e: Appid, model: Model) {
        model.addAttribute("companyName", companies.name(e.companyIdx))
    }
}
