package com.crosscert.fidoadmin.manager.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.LoginAttemptService;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.manager.web.ManagerSearchForm;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CCFA_MANAGER. SUPER 전용 URL. USER_PW 는 컨트롤러가 SHA-256 으로 인코딩해 넘긴다.
 * 잠금 상태는 CCFA_MANAGER_PW_POLICY 에 있고, 해제는 1부 LoginAttemptService.unlock 이 처리한다.
 */
@Service
public class ManagerService extends CrudService<CcfaManager, Long, ManagerSearchForm> {

    private final CcfaManagerRepository managers;
    private final CcfaManagerPwPolicyRepository policies;
    private final LoginAttemptService loginAttempts;
    private final EntityManager em;

    public ManagerService(CcfaManagerRepository managers, AuditLogger audit,
                          CcfaManagerPwPolicyRepository policies, LoginAttemptService loginAttempts,
                          EntityManager em) {
        super(managers, audit);
        this.managers = managers;
        this.policies = policies;
        this.loginAttempts = loginAttempts;
        this.em = em;
    }

    @Override protected Specification<CcfaManager> toSpecification(ManagerSearchForm f) {
        return Specs.all(
            Specs.like("userId", f.getUserId()),
            Specs.like("userNm", f.getUserNm()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
    @Override protected Long companyIdxOf(CcfaManager e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaManager e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaManager e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_MANAGER"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "userId", "userNm", "lastAccess"); }

    @Override protected void applyDefaults(CcfaManager e) {
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("활성");
        if (e.getLogin() == null) e.setLogin("OFF-LINE");
        if (e.getAlramType() == null || e.getAlramType().isBlank()) e.setAlramType("none");
        if (e.getAlramLevel() == null || e.getAlramLevel().isBlank()) e.setAlramLevel("0");
    }
    @Override protected void touchCreated(CcfaManager e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaManager e, LocalDateTime now) { e.setUpdatedtime(now); }

    /**
     * ERD 에 USER_ID 유니크 제약이 없고 스키마를 바꿀 수 없어, 존재 검사를 코드에서 직렬화해야 한다.
     * 그러지 않으면 동시에 등록 요청이 들어왔을 때 둘 다 findByUserId() 에서 빈 결과를 보고
     * 둘 다 INSERT 해 중복 USER_ID 가 생길 수 있다(로그인이 USER_ID 로 조회하므로 위험하다).
     * 트랜잭션이 끝날 때까지 테이블을 배타 잠금해 이후 생성 요청을 직렬화한다.
     * 운영자 등록은 SUPER 만 드물게 수행하므로 테이블 잠금 비용은 수용 가능하다.
     */
    @Override protected CcfaManager insert(CcfaManager e) {
        em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE").executeUpdate();
        if (managers.findByUserId(e.getUserId()).isPresent()) {
            throw new DataIntegrityViolationException("CCFA_MANAGER " + e.getUserId() + " 은(는) 이미 존재합니다");
        }
        return super.insert(e);
    }

    @Override protected void beforeDelete(CcfaManager e) {
        if (e.getIdx() != null && e.getIdx().equals(TenantContext.require().getIdx())) {
            throw new IllegalStateException("자기 자신은 삭제할 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<CcfaManagerPwPolicy> lockState(String userId) {
        return policies.findFirstByUserIdOrderByIdxDesc(userId);
    }

    /** 잠금 해제: PW_POLICY 초기화 + BLOCK_TIME 제거. 감사 로그 STATUS. */
    @Transactional
    public void unlock(Long id) {
        CcfaManager m = get(id);
        loginAttempts.unlock(m.getUserId());
        audit.log(AuditType.STATUS, "CCFA_MANAGER UNLOCK " + m.getUserId());
    }
}
