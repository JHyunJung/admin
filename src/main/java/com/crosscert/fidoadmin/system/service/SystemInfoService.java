package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import com.crosscert.fidoadmin.system.repository.CcfaSystemInfoRepository;
import com.crosscert.fidoadmin.system.web.SystemInfoSearchForm;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_SYSTEM_INFO — 문자열 PK(PROP_KEY). COMPANY_IDX 없음 → SUPER 전용. */
@Service
public class SystemInfoService extends AssignedIdCrudService<CcfaSystemInfo, String, SystemInfoSearchForm> {

    public SystemInfoService(CcfaSystemInfoRepository repository, AuditLogger audit, EntityManager em, TenantContext tenant) {
        super(repository, audit, em, tenant);
    }

    @Override protected Specification<CcfaSystemInfo> toSpecification(SystemInfoSearchForm f) {
        return Specs.all(Specs.like("propKey", f.getPropKey()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaSystemInfo e) { return null; }
    @Override protected void setCompanyIdx(CcfaSystemInfo e, Long c) {}
    @Override public String idOf(CcfaSystemInfo e) { return e.getPropKey(); }
    @Override protected String assignedId(CcfaSystemInfo e) { return e.getPropKey(); }
    @Override protected String tableName() { return "CCFA_SYSTEM_INFO"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "propKey"); }
    @Override public Set<String> sortableProperties() { return Set.of("propKey", "updatedtime"); }
    @Override protected void touchCreated(CcfaSystemInfo e, LocalDateTime now) { e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaSystemInfo e, LocalDateTime now) { e.setUpdatedtime(now); }
}
