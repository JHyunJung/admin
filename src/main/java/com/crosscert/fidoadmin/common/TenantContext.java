package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 현재 로그인 운영자와 <b>유효 테넌트</b>를 준다.
 *
 * <p>유효 테넌트는 "지금 보고 있는 고객사"다. COMPANY 는 항상 자기 소속이고,
 * SUPER 는 세션에서 고른 고객사다. 이 구분 덕분에 CrudService 는 역할을 묻지 않는다.
 *
 * <p>isSuper() 를 두지 않는다. "지금 보는 테넌트가 전역인가"는 이 모델에서
 * 성립하지 않는 질문이고, 남겨 두면 예전 의미(계정이 SUPER 인가)로 오용된다.
 * 계정 성질을 물어야 하는 곳은 {@code require().isSuper()} 를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class TenantContext {

    private final SelectedTenant selected;

    public Optional<ManagerUserDetails> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ManagerUserDetails user)) return Optional.empty();
        return Optional.of(user);
    }

    public ManagerUserDetails require() {
        return current().orElseThrow(() -> new IllegalStateException("로그인 사용자가 없습니다"));
    }

    /** 유효 테넌트. 미선택 SUPER 는 값이 없으므로 예외다. */
    public Long companyIdx() {
        ManagerUserDetails user = require();
        if (!user.isSuper()) return user.getCompanyIdx();
        return selected.companyIdx().orElseThrow(NoTenantSelectedException::new);
    }

    /** 유효 테넌트가 있는가. 인터셉터와 레이아웃이 분기에 쓴다. */
    public boolean hasTenant() {
        return current().map(u -> !u.isSuper() || selected.companyIdx().isPresent()).orElse(false);
    }
}
