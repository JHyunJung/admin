package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;
import com.crosscert.fidoadmin.fido.repository.TransactionConfirmationRepository;
import com.crosscert.fidoadmin.fido.web.TransactionConfirmationSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** TRANSACTION_CONFIRMATION 조회 전용. 시각 컬럼은 CREATEDTIME. */
@Service
public class TransactionConfirmationQueryService
        extends CrudService<TransactionConfirmation, Long, TransactionConfirmationSearchForm> {

    public TransactionConfirmationQueryService(TransactionConfirmationRepository repository, AuditLogger audit, TenantContext tenant) {
        super(repository, audit, tenant);
    }

    @Override protected Specification<TransactionConfirmation> toSpecification(TransactionConfirmationSearchForm f) {
        return Specs.all(
            Specs.like("userid", f.getUserid()),
            Specs.like("aaid", f.getAaid()),
            Specs.between("createdtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(TransactionConfirmation e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(TransactionConfirmation e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(TransactionConfirmation e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "TRANSACTION_CONFIRMATION"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createdtime", "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createdtime"); }
}
