package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import com.crosscert.fidoadmin.system.web.SystemPropSearchForm;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * CCFA_SYSTEM_PROP. 복합키(PROP_KEY, COMPANY_IDX) 가 폼에서 채워지므로 삽입 전용 경로를 쓴다.
 * SUPER 전용 URL(/system/**) 이지만 COMPANY_IDX 가 키 안에 있어 테넌트 속성은 "id.companyIdx" 로 둔다
 * (SUPER 의 고객사 필터에만 쓰인다).
 */
@Service
public class SystemPropService extends AssignedIdCrudService<CcfaSystemProp, CcfaSystemPropId, SystemPropSearchForm> {

    public SystemPropService(CcfaSystemPropRepository repository, AuditLogger audit, EntityManager em) {
        super(repository, audit, em);
    }

    @Override protected Specification<CcfaSystemProp> toSpecification(SystemPropSearchForm f) {
        return Specs.all(Specs.like("id.propKey", f.getPropKey()));
    }
    @Override protected String companyIdxAttribute() { return "id.companyIdx"; }
    @Override protected Long companyIdxOf(CcfaSystemProp e) { return e.getId() == null ? null : e.getId().getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaSystemProp e, Long c) {
        if (e.getId() == null) e.setId(new CcfaSystemPropId());
        e.getId().setCompanyIdx(c);
    }
    @Override public String idOf(CcfaSystemProp e) { return e.getId().toPathValue(); }
    @Override protected CcfaSystemPropId assignedId(CcfaSystemProp e) {
        CcfaSystemPropId id = e.getId();
        if (id == null || id.getPropKey() == null || id.getPropKey().isBlank() || id.getCompanyIdx() == null) return null;
        return id;
    }
    @Override protected String tableName() { return "CCFA_SYSTEM_PROP"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "id.propKey", "id.companyIdx"); }
    @Override public Set<String> sortableProperties() { return Set.of("id.propKey", "id.companyIdx", "updatedtime"); }

    @Override protected void applyDefaults(CcfaSystemProp e) {
        if (e.getShareType() == null || e.getShareType().isBlank()) e.setShareType("NO");
    }
    @Override protected void touchCreated(CcfaSystemProp e, LocalDateTime now) { e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaSystemProp e, LocalDateTime now) { e.setUpdatedtime(now); }
}
