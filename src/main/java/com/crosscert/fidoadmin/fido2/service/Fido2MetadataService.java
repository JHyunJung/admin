package com.crosscert.fidoadmin.fido2.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import com.crosscert.fidoadmin.fido2.repository.Fido2MetadataRepository;
import com.crosscert.fidoadmin.fido2.web.Fido2MetadataSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** FIDO2_METADATA. COMPANY_IDX 가 없어 SUPER 전용(설계 3.3). UPDATEDTIME 컬럼이 없다. */
@Service
public class Fido2MetadataService extends CrudService<Fido2Metadata, Long, Fido2MetadataSearchForm> {

    public Fido2MetadataService(Fido2MetadataRepository repository, AuditLogger audit) {
        super(repository, audit);
    }

    @Override protected Specification<Fido2Metadata> toSpecification(Fido2MetadataSearchForm f) {
        return Specs.all(
            Specs.like("aaguid", f.getAaguid()),
            Specs.like("description", f.getDescription()),
            Specs.like("protocolfamily", f.getProtocolfamily()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(Fido2Metadata e) { return null; }
    @Override protected void setCompanyIdx(Fido2Metadata e, Long c) {}
    @Override public String idOf(Fido2Metadata e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "FIDO2_METADATA"; }
    @Override public Set<String> sortableProperties() {
        return Set.of("idx", "description", "aaguid", "protocolfamily", "createdtime");
    }
    @Override protected void touchCreated(Fido2Metadata e, LocalDateTime now) { e.setCreatedtime(now); }
}
