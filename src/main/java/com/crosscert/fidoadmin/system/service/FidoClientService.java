package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository;
import com.crosscert.fidoadmin.system.web.FidoClientSearchForm;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_FIDOCLIENT — 문자열 PK(SERVERCODE). STATUS 기본 'ON'. SUPER 전용. */
@Service
public class FidoClientService extends AssignedIdCrudService<CcfaFidoclient, String, FidoClientSearchForm> {

    public FidoClientService(CcfaFidoclientRepository repository, AuditLogger audit, EntityManager em, TenantContext tenant) {
        super(repository, audit, em, tenant);
    }

    @Override protected Specification<CcfaFidoclient> toSpecification(FidoClientSearchForm f) {
        return Specs.all(
            Specs.like("servercode", f.getServercode()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaFidoclient e) { return null; }
    @Override protected void setCompanyIdx(CcfaFidoclient e, Long c) {}
    @Override public String idOf(CcfaFidoclient e) { return e.getServercode(); }
    @Override protected String assignedId(CcfaFidoclient e) { return e.getServercode(); }
    @Override protected String tableName() { return "CCFA_FIDOCLIENT"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "servercode"); }
    @Override public Set<String> sortableProperties() { return Set.of("servercode", "servername", "status", "updatedtime"); }

    @Override protected void applyDefaults(CcfaFidoclient e) {
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("ON");
    }
    @Override protected void touchCreated(CcfaFidoclient e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaFidoclient e, LocalDateTime now) { e.setUpdatedtime(now); }
}
