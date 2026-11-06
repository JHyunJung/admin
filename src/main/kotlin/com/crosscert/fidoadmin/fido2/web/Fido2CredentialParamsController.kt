package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams
import com.crosscert.fidoadmin.fido2.service.Fido2CredentialParamsService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/fido2/credential-params")
class Fido2CredentialParamsController(
    private val service: Fido2CredentialParamsService,
) : CrudController<Fido2CredentialParams, Long, Fido2CredentialParamsForm, Fido2CredentialParamsSearchForm>() {

    override fun service(): CrudService<Fido2CredentialParams, Long, Fido2CredentialParamsSearchForm> = service
    override fun basePath(): String = "/fido2/credential-params"
    override fun viewDir(): String = "fido2/credential-params"
    override fun newForm(): Fido2CredentialParamsForm = Fido2CredentialParamsForm()
    override fun toForm(e: Fido2CredentialParams): Fido2CredentialParamsForm = Fido2CredentialParamsForm.from(e)
    override fun toEntity(f: Fido2CredentialParamsForm): Fido2CredentialParams {
        val p = Fido2CredentialParams(); f.applyTo(p); return p
    }
    override fun applyForm(f: Fido2CredentialParamsForm, e: Fido2CredentialParams) { f.applyTo(e) }
}
