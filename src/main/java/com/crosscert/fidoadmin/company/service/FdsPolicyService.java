package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import com.crosscert.fidoadmin.company.web.FdsPolicySearchForm;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * CCFA_FDS_POLICY. PK 가 COMPANY_IDX 라 고객사당 1건이며 채번이 없다(할당형 PK).
 * 등록은 AssignedIdCrudService 의 존재 검사 + persist 경로를 탄다.
 * COMPANY 역할은 기반 create() 가 COMPANY_IDX 를 자기 값으로 강제하므로 자기 행만 만들 수 있다.
 */
@Service
public class FdsPolicyService extends AssignedIdCrudService<CcfaFdsPolicy, Long, FdsPolicySearchForm> {

    public FdsPolicyService(CcfaFdsPolicyRepository repository, AuditLogger audit, EntityManager em) {
        super(repository, audit, em);
    }

    @Override protected Specification<CcfaFdsPolicy> toSpecification(FdsPolicySearchForm f) { return null; }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaFdsPolicy e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaFdsPolicy e, Long c) { e.setCompanyIdx(c); }
    @Override protected Long assignedId(CcfaFdsPolicy e) { return e.getCompanyIdx(); }
    @Override public String idOf(CcfaFdsPolicy e) { return String.valueOf(e.getCompanyIdx()); }
    @Override protected String tableName() { return "CCFA_FDS_POLICY"; }
    /** idx 가 없는 엔티티: 기본 정렬 "idx" 를 그대로 두면 조회 시 500 이 난다. */
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "companyIdx"); }
    @Override public Set<String> sortableProperties() { return Set.of("companyIdx", "updatedtime"); }

    @Override protected void touchCreated(CcfaFdsPolicy e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaFdsPolicy e, LocalDateTime now) { e.setUpdatedtime(now); }
}
