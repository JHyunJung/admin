package com.crosscert.fidoadmin.manager.service;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * CCFA_MANAGER.USER_ID 전역 유일성을 지키는 유일한 자리.
 *
 * <p>ERD 에 USER_ID 유니크 제약이 없고 스키마를 바꿀 수 없어, 존재 검사를 코드에서
 * 직렬화해야 한다. 그러지 않으면 동시 등록 요청 둘이 모두 findByUserId() 에서
 * 빈 결과를 보고 둘 다 INSERT 해 중복 USER_ID 가 생길 수 있다(로그인이 USER_ID 로만
 * 조회하므로 위험하다).
 *
 * <p>{@link ManagerService}(테넌트 운영자, COMPANY_IDX &gt; 0)와
 * {@link SuperManagerService}(슈퍼관리자 계정, COMPANY_IDX = 0)가 같은 테이블에
 * INSERT 한다. USER_ID 중복은 테넌트와 무관하게 전역이어야 하므로(로그인 조회가
 * 테넌트로 걸러지지 않는다), 검사를 두 서비스에 각각 두면 "전역" 성질이 코드에
 * 드러나지 않고 한쪽만 고쳐질 위험이 생긴다. 잠금 → 존재 검사 → INSERT 순서를
 * 이 빈 하나에만 두고 두 서비스가 함께 호출한다.
 */
@Component
@RequiredArgsConstructor
public class ManagerUserIdGuard {

    private final CcfaManagerRepository managers;
    private final EntityManager em;

    /**
     * 테이블을 배타 잠금해 이후 생성 요청을 직렬화한 뒤 USER_ID 중복을 검사하고 저장한다.
     * 운영자 등록은 드물게 일어나므로 테이블 잠금 비용은 수용 가능하다.
     */
    public CcfaManager insert(CcfaManager entity) {
        em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE").executeUpdate();
        if (managers.findByUserId(entity.getUserId()).isPresent()) {
            throw new DataIntegrityViolationException("CCFA_MANAGER " + entity.getUserId() + " 은(는) 이미 존재합니다");
        }
        return managers.save(entity);
    }
}
