package com.crosscert.fidoadmin.common;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * 슈퍼관리자가 현재 보고 있는 고객사. 세션에 산다.
 *
 * <p>COMPANY 역할은 이 값을 쓰지 않는다. 자기 소속이 곧 유효 테넌트다.
 * 로그아웃의 invalidateHttpSession(true) 가 선택을 지운다.
 *
 * <p>고객사가 실재하는지는 여기서 검사하지 않는다. 그 판정에는 리포지터리가 필요하고,
 * 세션 빈이 영속 계층에 의존하면 테스트와 수명주기가 얽힌다. 호출자
 * (TenantSelectionController)가 검사한 뒤 넘긴다.
 */
@Component
@SessionScope
public class SelectedTenant {

    private Long companyIdx;

    public Optional<Long> companyIdx() { return Optional.ofNullable(companyIdx); }

    /** IDX 0(SUPER)과 null 은 테넌트가 아니므로 거부한다. */
    public void select(Long idx) {
        if (idx == null) throw new IllegalArgumentException("고객사를 선택해야 합니다");
        if (idx == 0L) throw new IllegalArgumentException("전역(IDX 0)은 테넌트로 선택할 수 없습니다");
        this.companyIdx = idx;
    }

    public void clear() { this.companyIdx = null; }
}
