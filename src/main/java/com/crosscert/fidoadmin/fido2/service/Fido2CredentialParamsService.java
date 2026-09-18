package com.crosscert.fidoadmin.fido2.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;
import com.crosscert.fidoadmin.fido2.repository.Fido2CredentialParamsRepository;
import com.crosscert.fidoadmin.fido2.web.Fido2CredentialParamsSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** FIDO2_CREDENTIAL_PARAMS. COMPANY_IDX 가 없어 SUPER 전용. UPDATEDTIME 컬럼 없음. */
@Service
public class Fido2CredentialParamsService extends CrudService<Fido2CredentialParams, Long, Fido2CredentialParamsSearchForm> {

    public Fido2CredentialParamsService(Fido2CredentialParamsRepository repository, AuditLogger audit, TenantContext tenant) {
        super(repository, audit, tenant);
    }

    @Override protected Specification<Fido2CredentialParams> toSpecification(Fido2CredentialParamsSearchForm f) {
        return Specs.all(
            Specs.like("credType", f.getCredType()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(Fido2CredentialParams e) { return null; }
    @Override protected void setCompanyIdx(Fido2CredentialParams e, Long c) {}
    @Override public String idOf(Fido2CredentialParams e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "FIDO2_CREDENTIAL_PARAMS"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "credType", "credAlg", "createdtime"); }

    /** ERD 기본값: CRED_TYPE 'public-key', STATUS 'T'. */
    @Override protected void applyDefaults(Fido2CredentialParams e) {
        if (e.getCredType() == null || e.getCredType().isBlank()) e.setCredType("public-key");
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("T");
    }
    @Override protected void touchCreated(Fido2CredentialParams e, LocalDateTime now) { e.setCreatedtime(now); }
}
