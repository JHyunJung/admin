package com.crosscert.fidoadmin.log.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.log.repository.FidoLogsRepository;
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** FIDO_LOGS 조회 전용(append-only 로그). create/update/delete 는 컨트롤러에서 노출하지 않는다. */
@Service
public class FidoLogQueryService extends CrudService<FidoLogs, Long, FidoLogSearchForm> {

    public FidoLogQueryService(FidoLogsRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<FidoLogs> toSpecification(FidoLogSearchForm f) {
        return Specs.all(
            Specs.like("servicename", f.getServicename()),
            Specs.like("serialcode", f.getSerialcode()),
            Specs.between("createdtime", f.fromDateTime(), f.toDateTimeExclusive()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(FidoLogs e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(FidoLogs e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(FidoLogs e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "FIDO_LOGS"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "createdtime", "idx"); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "createdtime", "servicename"); }
}
