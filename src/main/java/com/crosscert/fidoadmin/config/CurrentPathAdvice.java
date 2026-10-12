package com.crosscert.fidoadmin.config;

import com.crosscert.fidoadmin.common.MenuArea;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 레이아웃이 쓰는 전역 모델 속성. */
@ControllerAdvice
@RequiredArgsConstructor
public class CurrentPathAdvice {

    private final TenantContext tenant;
    private final MenuRegistry menus;
    private final CompanyLookup companies;

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("currentArea")
    public MenuArea currentArea(HttpServletRequest request) {
        return menus.areaOf(request.getRequestURI());
    }

    /** 사이드바 고객사 전환기에 표시할 이름. 미선택이거나 COMPANY 면 null. */
    @ModelAttribute("selectedCompanyName")
    public String selectedCompanyName() {
        if (tenant.current().isEmpty() || !tenant.hasTenant()) return null;
        return companies.name(tenant.companyIdx());
    }
}
