package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.web.CompanySearchForm;
import com.crosscert.fidoadmin.fido.repository.AppidRepository;
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** CCFA_COMPANY. SUPER 전용이므로 테넌트 필터 없음. IDX 0 은 전역 레코드라 삭제 불가. */
@Service
public class CompanyService extends CrudService<CcfaCompany, Long, CompanySearchForm> {

    private final AppidRepository appids;
    private final UserinfoRepository users;
    private final CcfaManagerRepository managers;

    public CompanyService(CcfaCompanyRepository repository, AuditLogger audit, AppidRepository appids,
                          UserinfoRepository users, CcfaManagerRepository managers) {
        super(repository, audit);
        this.appids = appids;
        this.users = users;
        this.managers = managers;
    }

    @Override protected Specification<CcfaCompany> toSpecification(CompanySearchForm f) {
        return Specs.all(
            Specs.like("companyName", f.getCompanyName()),
            Specs.eq("companyType", f.getCompanyType()),
            Specs.eq("enableType", f.getEnableType()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaCompany e) { return null; }
    @Override protected void setCompanyIdx(CcfaCompany e, Long c) {}
    @Override public String idOf(CcfaCompany e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_COMPANY"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "companyName"); }

    @Override protected void applyDefaults(CcfaCompany e) {
        if (e.getEnableType() == null) e.setEnableType("Y");
        if (e.getMaxAppid() == null) e.setMaxAppid(0L);
        if (e.getMaxAppserver() == null) e.setMaxAppserver(0L);
        if (e.getMaxUser() == null) e.setMaxUser(0L);
        if (e.getStarttime() == null) e.setStarttime(LocalDateTime.now());
        if (e.getEndtime() == null) e.setEndtime(LocalDateTime.of(9999, 12, 31, 23, 59, 59));
        if (e.getCreator() == null) e.setCreator(TenantContext.require().getIdx());
    }
    @Override protected void touchCreated(CcfaCompany e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaCompany e, LocalDateTime now) {
        e.setUpdatedtime(now);
        e.setUpdator(TenantContext.require().getIdx());
    }

    @Override protected void beforeDelete(CcfaCompany e) {
        if (e.getIdx() != null && e.getIdx() == 0L) throw new IllegalStateException("전역(IDX 0) 고객사는 삭제할 수 없습니다.");
        List<String> deps = new ArrayList<>();
        long a = appids.countByCompanyIdx(e.getIdx()); if (a > 0) deps.add("앱 ID " + a + "건");
        long u = users.countByCompanyIdx(e.getIdx()); if (u > 0) deps.add("사용자 " + u + "건");
        long m = managers.countByCompanyIdx(e.getIdx()); if (m > 0) deps.add("운영자 " + m + "건");
        if (!deps.isEmpty()) throw new IllegalStateException("하위 데이터가 있어 삭제할 수 없습니다: " + String.join(", ", deps));
    }
}
