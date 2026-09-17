package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Challenge;
import com.crosscert.fidoadmin.fido.repository.ChallengeRepository;
import com.crosscert.fidoadmin.fido.web.ChallengeSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CHALLENGE 조회 전용. create/update/delete 는 컨트롤러에서 노출하지 않는다. */
@Service
public class ChallengeQueryService extends CrudService<Challenge, Long, ChallengeSearchForm> {

    public ChallengeQueryService(ChallengeRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Challenge> toSpecification(ChallengeSearchForm f) {
        return Specs.all(
            Specs.like("userid", f.getUserid()),
            Specs.like("servicename", f.getServicename()),
            Specs.between("createtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Challenge e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Challenge e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Challenge e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CHALLENGE"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createtime", "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createtime"); }
}
