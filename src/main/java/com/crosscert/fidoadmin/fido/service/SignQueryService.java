package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Sign;
import com.crosscert.fidoadmin.fido.repository.SignRepository;
import com.crosscert.fidoadmin.fido.web.SignSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** SIGN 조회 전용. */
@Service
public class SignQueryService extends CrudService<Sign, Long, SignSearchForm> {

    public SignQueryService(SignRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Sign> toSpecification(SignSearchForm f) {
        return Specs.all(
            Specs.like("userid", f.getUserid()),
            Specs.eq("del", f.getDel()),
            Specs.between("createtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Sign e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Sign e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Sign e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "SIGN"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createtime", "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createtime"); }
}
