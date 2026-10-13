package com.crosscert.fidoadmin.manager.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.LoginAttemptService;
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.TenantMismatchException;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.manager.web.ManagerSearchForm;
import com.crosscert.fidoadmin.signup.SignupPolicy;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * COMPANY_IDX = 0 인 슈퍼관리자 계정 전용 화면. 공통 규칙은 {@link ManagerAccountService} 에 있다.
 *
 * <p>테넌트 선택 모델에서 IDX 0 은 선택할 수 없으므로(SelectedTenant.select),
 * 어떤 고객사를 골라도 슈퍼관리자 계정이 /managers 목록에 나오지 않는다.
 * 이 화면이 그 계정을 관리하는 유일한 자리다.
 *
 * <p>{@code ManagerService} 를 상속하지 않는다. 상속하면 유효 테넌트 경로(companyIdxAttribute
 * 가 "companyIdx" 를 돌려주고 tenant.companyIdx() 로 거는 경로)가 섞여, 미선택 SUPER 가
 * 이 화면을 열 때 {@code NoTenantSelectedException} 이 튀어나온다. 이 화면은 SYSTEM 영역이라
 * 선택 여부와 무관해야 하므로 테넌트 필터를 상수 0 으로 고정한다.
 */
@Service
public class SuperManagerService extends ManagerAccountService {

    public SuperManagerService(CcfaManagerRepository managers, AuditLogger audit,
                               CcfaManagerPwPolicyRepository policies,
                               LoginAttemptService loginAttempts, EntityManager em,
                               TenantContext tenant, ManagerUserIdGuard userIdGuard,
                               PasswordExpiryPolicy expiry) {
        super(managers, audit, policies, loginAttempts, em, tenant, userIdGuard, expiry);
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
     *
     * <p><b>대가(trade-off)</b>: null 을 돌려주면 {@code CrudService.update()} 의 쓰기 측
     * 정규화(mutator 실행 뒤 setCompanyIdx(e, tenant.companyIdx()) 로 강제하는 부분)도 건너뛴다.
     * checkTenant() 는 get(id) 시점, 즉 mutator 실행 <b>전</b> 에 걸리므로 "행을 읽을 때 COMPANY_IDX=0
     * 이었는가" 만 보장하고 "저장할 값도 0 인가" 는 보장하지 못한다. ManagerForm.applyTo() 는 폼이 실어온
     * companyIdx 를 그대로 엔티티에 쓰므로, 조작된 POST 로 companyIdx=7 을 보내면 이 화면이 관리하는
     * 행이 다른 테넌트로 옮겨질 수 있다(되돌릴 UI가 없는 편도 함정). 그래서 {@link #normalize} 가
     * mutator 실행 뒤 companyIdx 를 0 으로 다시 고정한다.
     */
    @Override protected String companyIdxAttribute() { return null; }

    /** 상수 0 으로 검사한다. 테넌트 운영자 행은 이 화면에서 열 수 없다. */
    @Override protected void checkTenant(CcfaManager e) {
        Long owner = e.getCompanyIdx();
        if (owner == null || !owner.equals(SignupPolicy.SUPER_COMPANY_IDX)) {
            throw new TenantMismatchException(tableName() + " " + idOf(e));
        }
    }

    /** 이 화면이 만드는 계정은 항상 슈퍼관리자다. 나머지 기본값은 공통 규칙과 같다. */
    @Override protected void applyDefaults(CcfaManager e) {
        e.setCompanyIdx(SignupPolicy.SUPER_COMPANY_IDX);
        super.applyDefaults(e);
    }

    /** "이 화면이 저장하는 행은 항상 COMPANY_IDX = 0" — mutator 가 어떤 값을 쓰든 마지막에 되돌린다. */
    @Override protected void normalize(CcfaManager e) {
        e.setCompanyIdx(SignupPolicy.SUPER_COMPANY_IDX);
    }
}
