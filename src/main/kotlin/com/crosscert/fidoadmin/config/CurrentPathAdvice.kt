package com.crosscert.fidoadmin.config

import com.crosscert.fidoadmin.common.MenuArea
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/** 레이아웃이 쓰는 전역 모델 속성. */
@ControllerAdvice
class CurrentPathAdvice(
    private val tenant: TenantContext,
    private val menus: MenuRegistry,
    private val companies: CompanyLookup,
) {

    @ModelAttribute("currentPath")
    fun currentPath(request: HttpServletRequest): String = request.requestURI

    @ModelAttribute("currentArea")
    fun currentArea(request: HttpServletRequest): MenuArea = menus.areaOf(request.requestURI)

    /** 사이드바 고객사 전환기에 표시할 이름. 미선택이거나 COMPANY 면 null. */
    @ModelAttribute("selectedCompanyName")
    fun selectedCompanyName(): String? {
        if (!tenant.hasTenant()) return null
        return companies.name(tenant.companyIdx())
    }
}
