package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable;
import com.crosscert.fidoadmin.system.repository.CcfaErrorTableRepository;
import com.crosscert.fidoadmin.system.web.ErrorCodeSearchForm;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_ERROR_TABLE — 문자열 PK(ERROR_CODE). 타임스탬프 컬럼 없음. SUPER 전용. */
@Service
public class ErrorCodeService extends AssignedIdCrudService<CcfaErrorTable, String, ErrorCodeSearchForm> {

    public ErrorCodeService(CcfaErrorTableRepository repository, AuditLogger audit, EntityManager em, TenantContext tenant) {
        super(repository, audit, em, tenant);
    }

    @Override protected Specification<CcfaErrorTable> toSpecification(ErrorCodeSearchForm f) {
        return Specs.all(
            Specs.like("errorCode", f.getErrorCode()),
            Specs.like("errorType", f.getErrorType()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaErrorTable e) { return null; }
    @Override protected void setCompanyIdx(CcfaErrorTable e, Long c) {}
    @Override public String idOf(CcfaErrorTable e) { return e.getErrorCode(); }
    @Override protected String assignedId(CcfaErrorTable e) { return e.getErrorCode(); }
    @Override protected String tableName() { return "CCFA_ERROR_TABLE"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "errorCode"); }
    @Override public Set<String> sortableProperties() { return Set.of("errorCode", "errorType"); }
}
