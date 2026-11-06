package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.fido.entity.Userinfo
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 단위 조회. USERINFO 는 한 행이 한 기기(크리덴셜)라 그대로 보여주면
 * 한 사람의 기기가 여러 행으로 흩어진다. 여기서 `(USERID, SERVICENAME)` 로 묶는다.
 *
 * `CrudService` 를 상속하지 않는다. 그쪽은 엔티티 한 행 단위 페이징이라
 * 그룹 조회에 맞지 않는다. 대신 **테넌트 경계를 직접 지킨다** — 모든 질의에
 * [TenantContext.companyIdx] 를 넘기며, 이 클래스가 유일한 경계다.
 */
@Service
class UserAccountService(
    private val repository: UserinfoRepository,
    private val tenant: TenantContext,
) {

    /** 사용자 단위 목록. 빈 검색어는 조건을 걸지 않도록 null 로 바꾼다. */
    @Transactional(readOnly = true)
    fun search(userid: String?, servicename: String?, pageable: Pageable): Page<UserAccountRow> {
        val company = tenant.companyIdx()
        val u = blankToNull(userid)
        val s = blankToNull(servicename)
        val rows = repository.findUserAccounts(company, u, s, pageable)
        val total = repository.countUserAccounts(company, u, s)
        return PageImpl(rows, pageable, total)
    }

    /**
     * 한 사용자의 기기 목록. 유효 테넌트 밖의 사용자는 **빈 목록**이다 —
     * 404 와 구분하지 않는다. 다른 고객사에 그 USERID 가 있는지조차 알려주지 않기 위해서다.
     */
    @Transactional(readOnly = true)
    fun credentialsOf(userid: String?, servicename: String?): List<Userinfo> =
        repository.findByCompanyIdxAndUseridAndServicenameOrderByRegtimeDesc(
            tenant.companyIdx(), userid, servicename,
        )

    private companion object {
        fun blankToNull(s: String?): String? = if (s.isNullOrJavaBlank()) null else s
    }
}
