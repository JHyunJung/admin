package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.repository.AppserverRepository;
import com.crosscert.fidoadmin.fido.web.AppserverSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** APPSERVER. COMPANY_IDX 로 테넌트 격리. ERD 기본값 TYPE='use'. */
@Service
public class AppserverService extends CrudService<Appserver, Long, AppserverSearchForm> {

    public AppserverService(AppserverRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Appserver> toSpecification(AppserverSearchForm f) {
        return Specs.all(
            Specs.like("memberCode", f.getMemberCode()),
            Specs.like("memberId", f.getMemberId()),
            Specs.eq("type", f.getType()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Appserver e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Appserver e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Appserver e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "APPSERVER"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "memberCode", "memberId", "createdtime"); }

    @Override protected void applyDefaults(Appserver e) {
        if (e.getType() == null || e.getType().isBlank()) e.setType("use");
    }
    @Override protected void touchCreated(Appserver e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(Appserver e, LocalDateTime now) { e.setUpdatedtime(now); }
}
