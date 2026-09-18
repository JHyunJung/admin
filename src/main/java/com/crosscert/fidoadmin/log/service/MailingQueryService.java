package com.crosscert.fidoadmin.log.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.log.repository.CcfaMailingRepository;
import com.crosscert.fidoadmin.log.web.MailingSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_MAILING(메일/SMS 발송 큐) 조회 전용. SENDTIME 은 문자열이라 정렬만 지원한다. */
@Service
public class MailingQueryService extends CrudService<CcfaMailing, Long, MailingSearchForm> {

    public MailingQueryService(CcfaMailingRepository repository, AuditLogger audit, TenantContext tenant) {
        super(repository, audit, tenant);
    }

    @Override protected Specification<CcfaMailing> toSpecification(MailingSearchForm f) {
        return Specs.all(
            Specs.eq("status", f.getStatus()),
            Specs.like("to", f.getTo()),
            Specs.eq("smsStatus", f.getSmsStatus()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaMailing e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaMailing e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaMailing e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_MAILING"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "status", "sendtime"); }
}
