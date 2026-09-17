package com.crosscert.fidoadmin.log.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.log.entity.CcfaExceptions;
import com.crosscert.fidoadmin.log.repository.CcfaExceptionsRepository;
import com.crosscert.fidoadmin.log.web.ExceptionLogSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_EXCEPTIONS 조회 전용. CREATEDTIME 은 문자열이므로 like 검색만 지원한다. */
@Service
public class ExceptionLogQueryService extends CrudService<CcfaExceptions, Long, ExceptionLogSearchForm> {

    public ExceptionLogQueryService(CcfaExceptionsRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<CcfaExceptions> toSpecification(ExceptionLogSearchForm f) {
        return Specs.all(
            Specs.like("eType", f.getEType()),
            Specs.eq("eLevel", f.getELevel()),
            Specs.like("exceptionMessage", f.getMessage()),
            Specs.like("createdtime", f.getCreatedtime()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaExceptions e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaExceptions e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaExceptions e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_EXCEPTIONS"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createdtime", "eType", "eLevel"); }
}
