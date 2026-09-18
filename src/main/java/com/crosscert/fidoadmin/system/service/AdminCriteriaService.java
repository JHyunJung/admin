package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import com.crosscert.fidoadmin.system.repository.CcfaCriteriaRepository;
import com.crosscert.fidoadmin.system.web.AdminCriteriaSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_CRITERIA — 어드민 인증기기 정책. COMPANY_IDX 가 없으므로 SUPER 전용. */
@Service
public class AdminCriteriaService extends CrudService<CcfaCriteria, Long, AdminCriteriaSearchForm> {

    public AdminCriteriaService(CcfaCriteriaRepository repository, AuditLogger audit, TenantContext tenant) {
        super(repository, audit, tenant);
    }

    @Override protected Specification<CcfaCriteria> toSpecification(AdminCriteriaSearchForm f) {
        return Specs.all(Specs.like("aaid", f.getAaid()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaCriteria e) { return null; }
    @Override protected void setCompanyIdx(CcfaCriteria e, Long c) {}
    @Override public String idOf(CcfaCriteria e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_CRITERIA"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "aaid", "updatedtime"); }

    @Override protected void touchCreated(CcfaCriteria e, LocalDateTime now) { e.setCreatetime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaCriteria e, LocalDateTime now) { e.setUpdatedtime(now); }
}
