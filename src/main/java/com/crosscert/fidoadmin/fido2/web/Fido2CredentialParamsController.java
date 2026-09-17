package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;
import com.crosscert.fidoadmin.fido2.service.Fido2CredentialParamsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/fido2/credential-params")
@RequiredArgsConstructor
public class Fido2CredentialParamsController
        extends CrudController<Fido2CredentialParams, Long, Fido2CredentialParamsForm, Fido2CredentialParamsSearchForm> {

    private final Fido2CredentialParamsService service;

    @Override protected CrudService<Fido2CredentialParams, Long, Fido2CredentialParamsSearchForm> service() { return service; }
    @Override protected String basePath() { return "/fido2/credential-params"; }
    @Override protected String viewDir() { return "fido2/credential-params"; }
    @Override protected Fido2CredentialParamsSearchForm newSearchForm() { return new Fido2CredentialParamsSearchForm(); }
    @Override protected Fido2CredentialParamsForm newForm() { return new Fido2CredentialParamsForm(); }
    @Override protected Fido2CredentialParamsForm toForm(Fido2CredentialParams e) { return Fido2CredentialParamsForm.from(e); }
    @Override protected Fido2CredentialParams toEntity(Fido2CredentialParamsForm f) {
        Fido2CredentialParams p = new Fido2CredentialParams(); f.applyTo(p); return p;
    }
    @Override protected void applyForm(Fido2CredentialParamsForm f, Fido2CredentialParams e) { f.applyTo(e); }
}
