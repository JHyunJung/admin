package com.crosscert.fidoadmin.manager.web

import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.service.ManagerService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/managers")
class ManagerController(
    private val service: ManagerService,
    private val companies: CompanyLookup,
    encoder: PasswordEncoder,
) : ManagerAccountController(service, encoder) {

    override fun service(): CrudService<CcfaManager, Long, ManagerSearchForm> = service
    override fun basePath(): String = "/managers"
    override fun viewDir(): String = "manager/manager"

    override fun populateDetailModel(e: CcfaManager, model: Model) {
        model.addAttribute("companyName", companies.name(e.companyIdx))
        model.addAttribute("lockState", service.lockState(e.userId).orElse(null))
    }
}
