package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.repository.AppserverRepository;
import com.crosscert.fidoadmin.fido.web.AppserverSearchForm;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** APPSERVER. COMPANY_IDX 로 테넌트 격리. ERD 기본값 TYPE='use'. */
@Service
public class AppserverService extends CrudService<Appserver, Long, AppserverSearchForm> {

    private final ApplicationEventPublisher events;
    private final MemberCodeGenerator memberCodes;

    public AppserverService(AppserverRepository repository, AuditLogger audit, TenantContext tenant,
                        ApplicationEventPublisher events, MemberCodeGenerator memberCodes) {
        super(repository, audit, tenant);
        this.events = events;
        this.memberCodes = memberCodes;
    }

    /** FIDO 서버가 멤버코드 를 캐시한다. 바뀌면 커밋 뒤 reload 를 보낸다(이전 어드민 sendAllSignal). */
    @Override protected void afterChange(String action, Appserver e) {
        events.publishEvent(new FidoConfigChanged(tableName() + " " + action + " " + idOf(e)));
    }

    /**
     * 현재 고객사에 같은 MEMBER_CODE + MEMBER_ID 행이 있는가. 수정이면 자기 행({@code excludeIdx})은 뺀다.
     * 이전 어드민 FidoController 의 membercode 중복 검사를 잇는다. DB 유니크 제약은 없다.
     */
    @Transactional(readOnly = true)
    public boolean existsDuplicate(String memberCode, String memberId, Long excludeIdx) {
        if (memberCode == null || memberCode.isBlank() || memberId == null || memberId.isBlank()) return false;
        AppserverRepository r = (AppserverRepository) repository;
        Long company = tenant.companyIdx();
        return excludeIdx == null
            ? r.existsByCompanyIdxAndMemberCodeAndMemberId(company, memberCode, memberId)
            : r.existsByCompanyIdxAndMemberCodeAndMemberIdAndIdxNot(company, memberCode, memberId, excludeIdx);
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

    /**
     * 등록 때만 불린다(CrudService.create). 멤버코드는 사람이 정하지 않는다 — 호출자가 무엇을
     * 넘겼든 새로 만든다. 수정 경로는 이 훅을 거치지 않으므로 한 번 정한 코드는 바뀌지 않는다.
     */
    @Override protected void applyDefaults(Appserver e) {
        if (e.getType() == null || e.getType().isBlank()) e.setType("use");
        e.setMemberCode(memberCodes.generate());
    }
    @Override protected void touchCreated(Appserver e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(Appserver e, LocalDateTime now) { e.setUpdatedtime(now); }
}
