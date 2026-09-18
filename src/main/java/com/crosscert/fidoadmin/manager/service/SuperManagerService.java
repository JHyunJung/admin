package com.crosscert.fidoadmin.manager.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.LoginAttemptService;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ManagerStatus;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.TenantMismatchException;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.manager.web.ManagerSearchForm;
import com.crosscert.fidoadmin.signup.SignupPolicy;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * COMPANY_IDX = 0 인 슈퍼관리자 계정 전용 화면.
 *
 * <p>테넌트 선택 모델에서 IDX 0 은 선택할 수 없으므로(SelectedTenant.select),
 * 어떤 고객사를 골라도 슈퍼관리자 계정이 /managers 목록에 나오지 않는다.
 * 이 화면이 그 계정을 관리하는 유일한 자리다.
 *
 * <p>{@code ManagerService} 를 상속하지 않는다. 상속하면 유효 테넌트 경로(companyIdxAttribute
 * 가 "companyIdx" 를 돌려주고 tenant.companyIdx() 로 거는 경로)가 섞여, 미선택 SUPER 가
 * 이 화면을 열 때 {@code NoTenantSelectedException} 이 튀어나온다. 이 화면은 SYSTEM 영역이라
 * 선택 여부와 무관해야 하므로 {@code CrudService} 를 직접 상속하고 테넌트 필터를
 * 상수 0 으로 고정한다.
 */
@Service
public class SuperManagerService extends CrudService<CcfaManager, Long, ManagerSearchForm> {

    private final CcfaManagerPwPolicyRepository policies;
    private final LoginAttemptService loginAttempts;
    private final EntityManager em;
    private final ManagerUserIdGuard userIdGuard;

    public SuperManagerService(CcfaManagerRepository managers, AuditLogger audit,
                               CcfaManagerPwPolicyRepository policies,
                               LoginAttemptService loginAttempts, EntityManager em,
                               TenantContext tenant, ManagerUserIdGuard userIdGuard) {
        super(managers, audit, tenant);
        this.policies = policies;
        this.loginAttempts = loginAttempts;
        this.em = em;
        this.userIdGuard = userIdGuard;
    }

    @Override protected Specification<CcfaManager> toSpecification(ManagerSearchForm f) {
        return Specs.all(
            Specs.eq("companyIdx", SignupPolicy.SUPER_COMPANY_IDX),
            Specs.like("userId", f.getUserId()),
            Specs.like("userNm", f.getUserNm()),
            Specs.eq("status", f.getStatus()));
    }

    /**
     * 이 테이블에는 실제로 COMPANY_IDX 컬럼이 있다. 그러나 이 화면이 걸어야 하는 값은
     * 유효 테넌트(tenant.companyIdx())가 아니라 상수 0 이므로, CrudService 의 테넌트 필터
     * 경로(search 의 companyIdxAttribute 기반 자동 필터, create 의 setCompanyIdx 자동 호출)를
     * 쓰지 않는다. 대신 toSpecification()/applyDefaults() 에서 0 을 직접 걸고,
     * checkTenant() 를 재정의해 0 이 아닌 행을 거부한다.
     *
     * <p>null 을 돌려주면 {@code requireSuperForGlobalTable()} 이 "COMPANY_IDX 가 없는
     * 전역 테이블" 로 보고 계정이 SUPER 인지 검사한다 — 이 화면은 SUPER 전용이므로
     * 그 부작용을 의도적으로 빌려 쓴다. URL 설정(hasRole("SUPER"))에 더한 서비스 계층의
     * 이중 방어다.
     */
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaManager e) { return e.getCompanyIdx(); }
    @Override protected void setCompanyIdx(CcfaManager e, Long c) { e.setCompanyIdx(c); }
    @Override public String idOf(CcfaManager e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_MANAGER"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "userId", "userNm", "lastAccess"); }

    /** 상수 0 으로 검사한다. 테넌트 운영자 행은 이 화면에서 열 수 없다. */
    @Override protected void checkTenant(CcfaManager e) {
        Long owner = e.getCompanyIdx();
        if (owner == null || !owner.equals(SignupPolicy.SUPER_COMPANY_IDX)) {
            throw new TenantMismatchException(tableName() + " " + idOf(e));
        }
    }

    @Override protected void applyDefaults(CcfaManager e) {
        e.setCompanyIdx(SignupPolicy.SUPER_COMPANY_IDX); // 이 화면이 만드는 계정은 항상 슈퍼관리자다
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus(ManagerStatus.ACTIVE);
        if (e.getLogin() == null) e.setLogin("OFF-LINE");
        if (e.getAlramType() == null || e.getAlramType().isBlank()) e.setAlramType("none");
        if (e.getAlramLevel() == null || e.getAlramLevel().isBlank()) e.setAlramLevel("0");
    }

    @Override protected void touchCreated(CcfaManager e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaManager e, LocalDateTime now) { e.setUpdatedtime(now); }

    /**
     * USER_ID 전역 유일성 검사는 {@link ManagerUserIdGuard} 하나에 있다. {@link ManagerService}
     * 도 같은 테이블에 INSERT 하므로, 이 화면에 검사를 따로 두면 "USER_ID 는 전역 유일"
     * 이라는 보장이 두 코드로 갈라진다.
     */
    @Override protected CcfaManager insert(CcfaManager e) {
        return userIdGuard.insert(e);
    }

    /**
     * 가입 신청 상태(승인대기·거절)인 행의 STATUS 는 이 화면에서 바꿀 수 없다.
     * {@code ManagerService.update()} 와 같은 규칙이다(설계서 4장) — 승인대기 행의 상태를
     * 조작된 POST 로 활성화하면 가입 승인 절차(감사 로그·상태 재확인·행 잠금)를 건너뛴다.
     * COMPANY_IDX = 0 계정도 가입 신청 상태를 거칠 수 있으므로 이 화면에도 같은 검사가 필요하다.
     */
    @Override
    @Transactional
    public CcfaManager update(Long id, java.util.function.Consumer<CcfaManager> mutator) {
        return super.update(id, e -> {
            String before = e.getStatus();
            mutator.accept(e);
            if (SignupPolicy.isSignupStatus(before) && !before.equals(e.getStatus())) {
                e.setStatus(before);
                throw new IllegalStateException(
                    "가입 신청 상태(" + before + ")인 계정의 상태는 가입 승인 화면에서만 변경할 수 있습니다.");
            }
        });
    }

    /**
     * 자기 자신은 삭제할 수 없다. {@code ManagerService.beforeDelete()} 와 같은 규칙이지만
     * 이 화면에서는 더 중요하다 — 슈퍼관리자가 자기 계정을 지우면 복구 경로가 없다.
     */
    @Override protected void beforeDelete(CcfaManager e) {
        if (e.getIdx() != null && e.getIdx().equals(tenant.require().getIdx())) {
            throw new IllegalStateException("자기 자신은 삭제할 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public Optional<CcfaManagerPwPolicy> lockState(String userId) {
        return policies.findFirstByUserIdOrderByIdxDesc(userId);
    }

    /** 잠금 해제: PW_POLICY 초기화 + BLOCK_TIME 제거. 감사 로그 STATUS. ManagerService.unlock() 과 같다. */
    @Transactional
    public void unlock(Long id) {
        CcfaManager m = get(id);
        em.refresh(m, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        loginAttempts.unlock(m.getUserId());
        audit.log(AuditType.STATUS, "CCFA_MANAGER UNLOCK " + m.getUserId());
    }
}
