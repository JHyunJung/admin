package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 현재 로그인 운영자와 테넌트(COMPANY_IDX) 조회. COMPANY_IDX 0 = SUPER. */
public final class TenantContext {

    private TenantContext() {}

    public static Optional<ManagerUserDetails> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ManagerUserDetails user)) return Optional.empty();
        return Optional.of(user);
    }

    public static ManagerUserDetails require() {
        return current().orElseThrow(() -> new IllegalStateException("로그인 사용자가 없습니다"));
    }

    public static Long companyIdx() { return require().getCompanyIdx(); }

    public static boolean isSuper() { return current().map(ManagerUserDetails::isSuper).orElse(false); }
}
