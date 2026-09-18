package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.repository.CriteriaRepository;
import com.crosscert.fidoadmin.fido.web.CriteriaSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * CRITERIA 조회 전용. COMPANY_IDX 가 없어 companyIdxAttribute() 는 null 이고,
 * 기반의 requireSuperForGlobalTable() 이 COMPANY 역할을 막는다(설계 3.3).
 */
@Service
public class CriteriaQueryService extends CrudService<Criteria, Long, CriteriaSearchForm> {

    public CriteriaQueryService(CriteriaRepository repository, AuditLogger audit, TenantContext tenant) {
        super(repository, audit, tenant);
    }

    @Override protected Specification<Criteria> toSpecification(CriteriaSearchForm f) {
        return Specs.all(Specs.like("aaid", f.getAaid()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(Criteria e) { return null; }
    @Override protected void setCompanyIdx(Criteria e, Long c) {}
    @Override public String idOf(Criteria e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CRITERIA"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "aaid", "updatedtime"); }
}
