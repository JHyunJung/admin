package com.crosscert.fidoadmin.config;

import com.crosscert.fidoadmin.common.MenuArea;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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

    /** 상단 선택기에 표시할 이름. 미선택이거나 COMPANY 면 null. */
    @ModelAttribute("selectedCompanyName")
    public String selectedCompanyName() {
        if (tenant.current().isEmpty() || !tenant.hasTenant()) return null;
        return companies.name(tenant.companyIdx());
    }

    /**
     * 선택기 드롭다운 목록. SUPER 에게만 채운다.
     * 전역(IDX 0)은 테넌트가 아니므로 뺀다.
     */
    @ModelAttribute("selectableCompanies")
    public List<CcfaCompany> selectableCompanies() {
        if (tenant.current().isEmpty() || !tenant.require().isSuper()) return List.of();
        return companies.all().stream().filter(c -> c.getIdx() != null && c.getIdx() != 0L).toList();
    }
}
