package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Appid;
import com.crosscert.fidoadmin.fido.repository.AppidRepository;
import com.crosscert.fidoadmin.fido.web.AppidSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** APPID. COMPANY_IDX 로 테넌트 격리. ERD 기본값 STATUS='use', DEVICE_DEFAULT='F'. */
@Service
public class AppidService extends CrudService<Appid, Long, AppidSearchForm> {

    public AppidService(AppidRepository repository, AuditLogger audit, TenantContext tenant) {
        super(repository, audit, tenant);
    }

    @Override protected Specification<Appid> toSpecification(AppidSearchForm f) {
        return Specs.all(
            Specs.like("appid", f.getAppid()),
            Specs.like("servicename", f.getServicename()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Appid e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Appid e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Appid e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "APPID"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "appid", "servicename", "createdtime"); }

    @Override protected void applyDefaults(Appid e) {
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("use");
        if (e.getDeviceDefault() == null || e.getDeviceDefault().isBlank()) e.setDeviceDefault("F");
    }
    @Override protected void touchCreated(Appid e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(Appid e, LocalDateTime now) { e.setUpdatedtime(now); }
}
