package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.fido.entity.Userinfo;
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository;
import com.crosscert.fidoadmin.fido.web.UserSearchForm;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * USERINFO 조회 + 상태 변경. 등록·수정·삭제는 컨트롤러에서 노출하지 않는다(설계 1.2 "조회 + 상태 변경").
 * 상태는 'O'(정상) / 'X'(해지) 만 허용한다.
 */
@Service
public class UserinfoService extends CrudService<Userinfo, Long, UserSearchForm> {

    public static final Set<String> STATUSES = Set.of("O", "X");

    public UserinfoService(UserinfoRepository repository, AuditLogger audit, TenantContext tenant) {
        super(repository, audit, tenant);
    }

    @Override protected Specification<Userinfo> toSpecification(UserSearchForm f) {
        return Specs.all(
            Specs.like("userid", f.getUserid()),
            Specs.like("servicename", f.getServicename()),
            Specs.like("aaid", f.getAaid()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(Userinfo e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(Userinfo e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(Userinfo e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "USERINFO"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "userid", "servicename", "regtime"); }

    /** 상태 변경. get() 이 테넌트 검사를 한다. 감사 로그 TYPE=STATUS. */
    @Transactional
    public Userinfo changeStatus(Long id, String status) {
        if (status == null || !STATUSES.contains(status)) {
            throw new IllegalArgumentException("허용되지 않는 상태입니다: " + status);
        }
        Userinfo u = get(id);
        String old = u.getStatus();
        u.setStatus(status);
        Userinfo saved = repository.save(u);
        audit.log(AuditType.STATUS, "USERINFO STATUS " + idOf(saved) + " " + old + "->" + status);
        return saved;
    }
}
