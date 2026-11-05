package com.crosscert.fidoadmin.manager.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.LoginAttemptService;
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import com.crosscert.fidoadmin.manager.web.ManagerSearchForm;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * CCFA_MANAGER — 테넌트 운영자(유효 테넌트의 COMPANY_IDX). SUPER 전용 URL.
 * USER_PW 는 컨트롤러가 SHA-256 으로 인코딩해 넘긴다. 공통 규칙은 {@link ManagerAccountService} 에 있다.
 * 잠금 상태는 CCFA_MANAGER_PW_POLICY 에 있고, 해제는 LoginAttemptService.unlock 이 처리한다.
 */
@Service
public class ManagerService extends ManagerAccountService {

    public ManagerService(CcfaManagerRepository managers, AuditLogger audit,
                          CcfaManagerPwPolicyRepository policies, LoginAttemptService loginAttempts,
                          EntityManager em, TenantContext tenant, ManagerUserIdGuard userIdGuard,
                          PasswordExpiryPolicy expiry) {
        super(managers, audit, policies, loginAttempts, em, tenant, userIdGuard, expiry);
    }

    @Override protected Specification<CcfaManager> toSpecification(ManagerSearchForm f) {
        return Specs.all(
            Specs.like("userId", f.getUserId()),
            Specs.like("userNm", f.getUserNm()),
            Specs.eq("status", f.getStatus()));
    }

    /** 유효 테넌트로 거른다. 등록·수정 때 소속도 CrudService 가 유효 테넌트로 강제한다. */
    @Override protected String companyIdxAttribute() { return "companyIdx"; }
}
