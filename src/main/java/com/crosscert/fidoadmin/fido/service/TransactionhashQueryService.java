package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Transactionhash;
import com.crosscert.fidoadmin.fido.repository.TransactionhashRepository;
import com.crosscert.fidoadmin.fido.web.TransactionhashSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** TRANSACTIONHASH 조회 전용. */
@Service
public class TransactionhashQueryService extends CrudService<Transactionhash, Long, TransactionhashSearchForm> {

    public TransactionhashQueryService(TransactionhashRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Transactionhash> toSpecification(TransactionhashSearchForm f) {
        return Specs.all(
            Specs.like("userid", f.getUserid()),
            Specs.between("createtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Transactionhash e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Transactionhash e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Transactionhash e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "TRANSACTIONHASH"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createtime", "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createtime"); }
}
