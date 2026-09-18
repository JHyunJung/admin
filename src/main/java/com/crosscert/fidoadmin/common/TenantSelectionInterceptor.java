package com.crosscert.fidoadmin.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 테넌트 영역 화면은 고객사가 선택된 뒤에만 열린다.
 *
 * <p>이것은 편의이지 방어선이 아니다. 실제 격리는 CrudService 가 유효 테넌트로
 * 건다. 인터셉터가 없어도 데이터는 새지 않고 NoTenantSelectedException 이 날 뿐이다.
 * 여기서 막는 이유는 운영자에게 오류 대신 선택 화면을 보여 주기 위해서다.
 */
@Component
@RequiredArgsConstructor
public class TenantSelectionInterceptor implements HandlerInterceptor {

    private final TenantContext tenant;
    private final MenuRegistry menus;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (tenant.current().isEmpty()) return true;            // 인증 전 — 시큐리티가 처리한다
        if (menus.areaOf(request.getRequestURI()) != MenuArea.TENANT) return true;
        if (tenant.hasTenant()) return true;
        response.sendRedirect(request.getContextPath() + "/select-tenant");
        return false;
    }
}
