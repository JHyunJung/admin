package com.crosscert.fidoadmin.fido2.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import com.crosscert.fidoadmin.fido2.repository.Fido2DemoAccessCodeRepository;
import com.crosscert.fidoadmin.fido2.web.Fido2DemoAccessCodeSearchForm;
import jakarta.persistence.EntityManager;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * FIDO2_DEMO_ACCESS_CODE. 할당형 문자열 PK(ACCESSCODE) 라 존재 검사 + persist 로 등록한다.
 * COMPANY_IDX 가 없어 SUPER 전용. 타임스탬프 컬럼이 없다.
 */
@Service
public class Fido2DemoAccessCodeService
        extends AssignedIdCrudService<Fido2DemoAccessCode, String, Fido2DemoAccessCodeSearchForm> {

    public Fido2DemoAccessCodeService(Fido2DemoAccessCodeRepository repository, AuditLogger audit, EntityManager em) {
        super(repository, audit, em);
    }

    @Override protected Specification<Fido2DemoAccessCode> toSpecification(Fido2DemoAccessCodeSearchForm f) {
        return Specs.all(
            Specs.like("accesscode", f.getAccesscode()),
            Specs.like("vendorname", f.getVendorname()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(Fido2DemoAccessCode e) { return null; }
    @Override protected void setCompanyIdx(Fido2DemoAccessCode e, Long c) {}
    @Override protected String assignedId(Fido2DemoAccessCode e) { return e.getAccesscode(); }
    @Override public String idOf(Fido2DemoAccessCode e) { return e.getAccesscode(); }
    @Override protected String tableName() { return "FIDO2_DEMO_ACCESS_CODE"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "accesscode"); }
    @Override public Set<String> sortableProperties() { return Set.of("accesscode", "vendorname", "status"); }

    /** ERD 기본값: STATUS 'E'(활성). */
    @Override protected void applyDefaults(Fido2DemoAccessCode e) {
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("E");
    }
}
