package com.crosscert.fidoadmin.log.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import com.crosscert.fidoadmin.log.repository.CcfaAuditLogRepository;
import com.crosscert.fidoadmin.log.web.AuditLogSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_AUDIT_LOG 조회 전용. create/update/delete 는 컨트롤러에서 노출하지 않는다. */
@Service
public class AuditLogQueryService extends CrudService<CcfaAuditLog, Long, AuditLogSearchForm> {

    public AuditLogQueryService(CcfaAuditLogRepository repository, AuditLogger audit, TenantContext tenant) {
        super(repository, audit, tenant);
    }

    @Override protected Specification<CcfaAuditLog> toSpecification(AuditLogSearchForm f) {
        return Specs.all(
            Specs.like("userId", f.getUserId()),
            Specs.eq("type", f.getType()),
            Specs.like("message", f.getMessage()),
            Specs.between("createdtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaAuditLog e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaAuditLog e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaAuditLog e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_AUDIT_LOG"; }
    /**
     * 시퀀스 PK 역순. 행이 많은 로그 테이블이라 CREATEDTIME 로 정렬하면 매 조회가 전체 정렬이 되고,
     * IDX 로 정렬하면 PK 인덱스를 거꾸로 읽어 첫 페이지만 가져온다. 생성 순서와 거의 같다
     * (같은 시각에 들어온 행끼리는 순서가 다를 수 있다). 시각 정렬은 목록 머리글로 고를 수 있다.
     */
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createdtime"); }
}
