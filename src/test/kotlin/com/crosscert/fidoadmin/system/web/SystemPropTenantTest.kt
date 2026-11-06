package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.system.service.SystemPropService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * 시스템 설정은 복합키(PROP_KEY + COMPANY_IDX)다. 등록은 폼이 아니라 유효 테넌트로
 * companyIdx 를 채워야 한다(고객사 select 가 사라졌으므로 폼에는 값이 없다).
 * URL 크래프팅 방어(checkTenant() 가 이미 처리한 부분)의 확인은 [SystemPropUrlTenantTest] 에 있다.
 */
class SystemPropTenantTest {

    private val selected = SelectedTenant()
    private val tenant = TenantContext(selected)
    private val controller = SystemPropController(
        mock<SystemPropService>(), mock<CompanyLookup>(), tenant)

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun loginSuperSelecting(targetIdx: Long) {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼관리자", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(u, null, u.authorities)
        selected.select(targetIdx)
    }

    private fun form(key: String, value: String): SystemPropForm {
        val f = SystemPropForm()
        f.propKey = key; f.propValue = value; f.shareType = "NO"
        return f
    }

    @Test fun 시스템_설정_등록은_선택한_고객사를_PK_로_쓴다() {
        loginSuperSelecting(9L)
        val e = controller.toEntity(form("PW_FAIL_LIMIT", "5"))
        assertThat(e.id!!.companyIdx).isEqualTo(9L)
    }
}
