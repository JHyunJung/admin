package com.crosscert.fidoadmin.signup;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 신청 저장. 인증 없이 호출되므로 {@code common.CrudService} 를 상속하지 않는다.
 * {@code CrudService.create()} 는 테넌트가 있는 테이블이면 {@code TenantContext.companyIdx()} 를
 * 부르고, 이는 {@code require()} 로 이어져 로그인 사용자가 없으면 예외를 던진다.
 *
 * <p>감사 로그는 남지 않는다. AuditLogger 는 로그인 사용자가 없으면 기록하지 않기 때문이며,
 * 이는 의도된 동작이다(설계서 참조).
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    private final CcfaManagerRepository managers;
    private final EntityManager em;

    @Transactional(readOnly = true)
    public boolean existsUserId(String userId) {
        return managers.findByUserId(userId).isPresent();
    }

    /**
     * 신청을 저장한다. 상태와 소속은 입력과 무관하게 강제된다(권한 상승 차단 1단계).
     * 상태·소속을 받을 파라미터 자체가 없으므로 호출자가 값을 끼워 넣을 통로가 없다.
     *
     * <p>USER_ID 에 DB 유니크 제약이 없어 동시 신청 시 중복이 생길 수 있다.
     * {@code ManagerService.insert()} 와 같은 방식으로 테이블을 배타 잠금해 직렬화한다.
     * 가입은 드물게 일어나므로 테이블 잠금 비용은 수용 가능하다.
     */
    @Transactional
    public CcfaManager apply(String userId, String encodedPw, String userNm,
                             String userEmail, String userPhone, String reason) {
        em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE").executeUpdate();
        if (managers.findByUserId(userId).isPresent()) {
            throw new DataIntegrityViolationException("CCFA_MANAGER " + userId + " 은(는) 이미 존재합니다");
        }
        LocalDateTime now = LocalDateTime.now();
        CcfaManager m = new CcfaManager();
        // IDX 는 CCFA_MANAGER_SEQ 가 채번한다. 직접 넣지 않는다.
        m.setUserId(userId);
        m.setUserPw(encodedPw);
        m.setUserNm(userNm);
        m.setUserEmail(userEmail);
        m.setUserPhone(userPhone);
        // 승인 전까지 로그인 불가. 소속은 미배정(-1)이며 SUPER 인 0 이 될 수 없다.
        m.setStatus(SignupPolicy.STATUS_PENDING);
        m.setCompanyIdx(SignupPolicy.UNASSIGNED_COMPANY_IDX);
        m.setLogin("OFF-LINE");
        m.setAlramType("none");
        m.setAlramLevel("0");
        m.setEtc(reason == null || reason.isBlank() ? null : "신청 사유: " + reason);
        m.setCreatedtime(now);
        m.setUpdatedtime(now);
        return managers.save(m);
    }
}
