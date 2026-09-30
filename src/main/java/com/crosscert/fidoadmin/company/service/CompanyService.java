package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService;
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged;
import org.springframework.context.ApplicationEventPublisher;
import com.crosscert.fidoadmin.company.web.CompanySearchForm;
import com.crosscert.fidoadmin.fido.repository.AppidRepository;
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CCFA_COMPANY. SUPER 전용이므로 테넌트 필터 없음. IDX 0 은 전역 레코드라 삭제 불가. */
@Service
public class CompanyService extends CrudService<CcfaCompany, Long, CompanySearchForm> {

    /** 고객사별로 따로 갖는 설정. 회사 0 의 이 값들이 새 고객사의 기본값이 된다. */
    static final String SHARE_TYPE_PER_COMPANY = "NO";

    private final AppidRepository appids;
    private final UserinfoRepository users;
    private final CcfaManagerRepository managers;
    private final CcfaSystemPropRepository props;
    private final CcfaFdsPolicyRepository fdsPolicies;
    private final CriteriaQueryService criteria;
    private final ApplicationEventPublisher events;

    public CompanyService(CcfaCompanyRepository repository, AuditLogger audit, AppidRepository appids,
                          UserinfoRepository users, CcfaManagerRepository managers,
                          CcfaSystemPropRepository props, TenantContext tenant,
                          CcfaFdsPolicyRepository fdsPolicies, CriteriaQueryService criteria,
                          ApplicationEventPublisher events) {
        super(repository, audit, tenant);
        this.appids = appids;
        this.users = users;
        this.managers = managers;
        this.props = props;
        this.fdsPolicies = fdsPolicies;
        this.criteria = criteria;
        this.events = events;
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
        if (e.getCreator() == null) e.setCreator(tenant.require().getIdx());
    }
    @Override protected void touchCreated(CcfaCompany e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaCompany e, LocalDateTime now) {
        e.setUpdatedtime(now);
        e.setUpdator(tenant.require().getIdx());
    }

    /**
     * 고객사를 만들고, 회사 0 의 고객사별 설정(SHARE_TYPE='NO')을 새 고객사로 복사한다.
     *
     * <p>이전 어드민의 {@code manager.insertSystemProp} 을 잇는다. 복사하지 않아도 화면은
     * {@code FidoSettingKey} 기본값으로 동작하지만, 설정 행이 없는 고객사와 있는 고객사가
     * 섞이면 "왜 이 고객사만 값이 없나"를 매번 설명해야 한다. 생성 시점에 맞춰 둔다.
     */
    @Override
    @Transactional
    public CcfaCompany create(CcfaCompany entity) {
        CcfaCompany saved = super.create(entity);
        int copied = copyPerCompanyProps(saved.getIdx());
        if (copied > 0) {
            audit.log(AuditType.CREATE,
                "CCFA_SYSTEM_PROP COPY 고객사 " + saved.getIdx() + " ← 회사 0 (" + copied + "건)");
        }
        // 이전 어드민 disableCompanyAllAAID: 새 고객사는 인증기를 하나씩 허용하기 전까지 FIDO 등록이 되지 않는다.
        int disabled = criteria.disableAllFor(saved.getIdx());
        if (disabled > 0) {
            audit.log(AuditType.CREATE, "CCFA_COMPANY_AAID 전체 차단 고객사 " + saved.getIdx() + " (" + disabled + "건)");
        }
        // 이전 어드민 fds.insert: 국가 조건 'NO' 인 빈 정책을 둔다.
        if (!fdsPolicies.existsById(saved.getIdx())) {
            LocalDateTime now = LocalDateTime.now();
            CcfaFdsPolicy policy = new CcfaFdsPolicy();
            policy.setCompanyIdx(saved.getIdx());
            policy.setAndCountry("NO");
            policy.setOrCountry("NO");
            policy.setCreatedtime(now);
            policy.setUpdatedtime(now);
            fdsPolicies.save(policy);
            audit.log(AuditType.CREATE, "CCFA_FDS_POLICY 기본값 고객사 " + saved.getIdx());
        }
        events.publishEvent(new FidoConfigChanged("고객사 생성 " + saved.getIdx()));
        return saved;
    }

    /**
     * 고객사를 지우고, 그 고객사만 쓰던 설정 행도 함께 지운다.
     *
     * <p>하위 데이터 검사({@link #beforeDelete})는 {@code super.delete} 안에서 먼저 돈다.
     * 거기서 막히면 예외로 빠져나오므로 설정 행은 건드리지 않는다.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        super.delete(id);
        List<CcfaSystemProp> rows = props.findAll(perCompanyProps(id));
        if (!rows.isEmpty()) {
            props.deleteAll(rows);
            audit.log(AuditType.DELETE, "CCFA_SYSTEM_PROP DELETE 고객사 " + id + " (" + rows.size() + "건)");
        }
        int aaidRows = criteria.deleteAllFor(id);
        if (aaidRows > 0) {
            audit.log(AuditType.DELETE, "CCFA_COMPANY_AAID DELETE 고객사 " + id + " (" + aaidRows + "건)");
        }
        if (fdsPolicies.existsById(id)) {
            fdsPolicies.deleteById(id);
            audit.log(AuditType.DELETE, "CCFA_FDS_POLICY DELETE 고객사 " + id);
        }
    }

    /** VENDOR_CODE 로 고객사를 찾는다. 중복 검사용 — DB 에 유니크 제약이 없어 화면에서 막는다. */
    @Transactional(readOnly = true)
    public Optional<CcfaCompany> findByVendorCode(String vendorCode) {
        if (vendorCode == null || vendorCode.isBlank()) return Optional.empty();
        return ((CcfaCompanyRepository) repository).findFirstByVendorCode(vendorCode.trim());
    }

    private int copyPerCompanyProps(Long companyIdx) {
        // 회사 0 자체를 만드는 경우는 없지만, 있더라도 자기 자신에게 복사하지 않는다.
        if (companyIdx == null || companyIdx == 0L) return 0;
        LocalDateTime now = LocalDateTime.now();
        int copied = 0;
        for (CcfaSystemProp template : props.findAll(perCompanyProps(0L))) {
            CcfaSystemPropId id = new CcfaSystemPropId(template.getId().getPropKey(), companyIdx);
            // 같은 키가 이미 있으면 그대로 둔다 — 복사는 기본값 제공이지 덮어쓰기가 아니다.
            if (props.existsById(id)) continue;
            CcfaSystemProp row = new CcfaSystemProp();
            row.setId(id);
            row.setPropValue(template.getPropValue());
            row.setShareType(SHARE_TYPE_PER_COMPANY);
            row.setUpdatedtime(now);
            props.save(row);
            copied++;
        }
        return copied;
    }

    private static Specification<CcfaSystemProp> perCompanyProps(Long companyIdx) {
        return Specs.all(Specs.eq("id.companyIdx", companyIdx), Specs.eq("shareType", SHARE_TYPE_PER_COMPANY));
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
