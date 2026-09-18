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
import com.crosscert.fidoadmin.signup.SignupPolicy;
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
                          EntityManager em, TenantContext tenant) {
        super(managers, audit, tenant);
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

    /**
     * 가입 신청 상태(승인대기·거절)인 행의 STATUS 는 이 화면에서 바꿀 수 없다.
     *
     * <p>수정 화면의 상태 선택지는 활성·비활성 뿐이라 승인대기를 표현하지 못한다. 그래서 승인대기 행을
     * 열면 브라우저가 활성을 고르고, 이름만 고쳐 저장해도 상태가 활성이 된다. 그러면
     * {@code SignupService.approve()} 를 거치지 않으므로 승인 감사 로그가 남지 않고
     * 승인대기 여부 재확인과 행 잠금도 건너뛴다(설계서 4장).
     *
     * <p>검사를 <b>서비스</b>에 두는 이유: 템플릿은 조작된 POST 를 막지 못하고, 컨트롤러의
     * {@code validate()} 훅은 폼만 받아 대상 행의 <b>현재</b> 상태를 알 수 없다. 현재 상태를 볼 수 있는
     * 지점이자 {@code update()} 를 부르는 모든 호출자에 한 번에 적용되는 곳이 여기다.
     * 값을 조용히 무시하지 않고 예외를 던지는 것은, 관리자가 바꿨다고 믿은 값이 반영되지 않은 채
     * "수정되었습니다" 만 뜨는 편이 더 나쁘기 때문이다.
     *
     * <p>막는 것은 상태 변경뿐이다. 같은 행의 이름·연락처 등 다른 필드 수정과,
     * 정상 상태(활성·비활성) 행의 상태 변경은 기존과 똑같이 동작한다.
     */
    @Override
    @Transactional
    public CcfaManager update(Long id, java.util.function.Consumer<CcfaManager> mutator) {
        return super.update(id, e -> {
            String before = e.getStatus();
            mutator.accept(e);
            if (SignupPolicy.isSignupStatus(before) && !before.equals(e.getStatus())) {
                // 던지기 전에 되돌린다. 트랜잭션 롤백에만 기대면, 롤백 경계 밖에서 이 엔티티를
                // 다시 읽는 코드(같은 영속성 컨텍스트, 테스트의 목 객체)가 바뀐 값을 보게 된다.
                e.setStatus(before);
                throw new IllegalStateException(
                    "가입 신청 상태(" + before + ")인 계정의 상태는 가입 승인 화면에서만 변경할 수 있습니다.");
            }
        });
    }

    @Override protected void beforeDelete(CcfaManager e) {
        if (e.getIdx() != null && e.getIdx().equals(tenant.require().getIdx())) {
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
        // get() 은 잠금 없이 읽는다. PESSIMISTIC_WRITE 로 재조회(refresh)해 FOR UPDATE 로 행을 다시 읽어야
        // 이후 save() 가 동시 로그인으로 바뀐 값(LOGIN, LAST_ACCESS, BLOCK_TIME, UPDATEDTIME)을 덮어쓰지 않는다.
        em.refresh(m, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        loginAttempts.unlock(m.getUserId());
        audit.log(AuditType.STATUS, "CCFA_MANAGER UNLOCK " + m.getUserId());
    }
}
