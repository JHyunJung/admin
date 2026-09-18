package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository;
import com.crosscert.fidoadmin.company.web.LicenseSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_LICENSE. SUPER 전용 URL. COMPANY_NAME 은 비정규화 컬럼이라 CCFA_COMPANY 에서 동기화한다. */
@Service
public class LicenseService extends CrudService<CcfaLicense, Long, LicenseSearchForm> {

    private final CcfaCompanyRepository companies;

    public LicenseService(CcfaLicenseRepository repository, AuditLogger audit, CcfaCompanyRepository companies, TenantContext tenant) {
        super(repository, audit, tenant);
        this.companies = companies;
    }

    @Override protected Specification<CcfaLicense> toSpecification(LicenseSearchForm f) {
        return Specs.all(Specs.like("serviceName", f.getServiceName()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaLicense e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaLicense e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaLicense e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_LICENSE"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "serviceName", "createdtime"); }

    @Override protected void applyDefaults(CcfaLicense e) { syncCompanyName(e); }
    @Override protected void touchCreated(CcfaLicense e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaLicense e, LocalDateTime now) { e.setUpdatedtime(now); syncCompanyName(e); }

    /** COMPANY_IDX 로 CCFA_COMPANY 이름을 찾아 COMPANY_NAME 에 복사한다. 없으면 null 로 둔다. */
    void syncCompanyName(CcfaLicense e) {
        if (e.getCompanyIdx() == null) { e.setCompanyName(null); return; }
        e.setCompanyName(companies.findById(e.getCompanyIdx()).map(CcfaCompany::getCompanyName).orElse(null));
    }
}
