package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import java.util.Optional
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 현재 로그인 운영자와 **유효 테넌트**를 준다.
 *
 * 유효 테넌트는 "지금 보고 있는 고객사"다. COMPANY 는 항상 자기 소속이고,
 * SUPER 는 세션에서 고른 고객사다. 이 구분 덕분에 CrudService 는 역할을 묻지 않는다.
 *
 * isSuper() 를 두지 않는다. "지금 보는 테넌트가 전역인가"는 이 모델에서
 * 성립하지 않는 질문이고, 남겨 두면 예전 의미(계정이 SUPER 인가)로 오용된다.
 * 계정 성질을 물어야 하는 곳은 `require().isSuper` 를 쓴다.
 */
@Component
class TenantContext(private val selected: SelectedTenant) {

    fun current(): Optional<ManagerUserDetails> {
        val auth = SecurityContextHolder.getContext().authentication
        val user = auth?.principal as? ManagerUserDetails ?: return Optional.empty()
        return Optional.of(user)
    }

    fun require(): ManagerUserDetails =
        current().orElseThrow { IllegalStateException("로그인 사용자가 없습니다") }

    /** 유효 테넌트. 미선택 SUPER 는 값이 없으므로 예외다. */
    fun companyIdx(): Long? {
        val user = require()
        if (!user.isSuper) return user.companyIdx
        return selected.companyIdx().orElseThrow { NoTenantSelectedException() }
    }

    /** 유효 테넌트가 있는가. 인터셉터와 레이아웃이 분기에 쓴다. */
    fun hasTenant(): Boolean =
        current().map { u -> !u.isSuper || selected.companyIdx().isPresent }.orElse(false)
}
