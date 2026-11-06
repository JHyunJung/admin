package com.crosscert.fidoadmin.company.service

import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 고객사 IDX → 이름 조회. 화면 표시와 select 옵션에 사용. */
@Service
class CompanyLookup(
    private val repository: CcfaCompanyRepository,
    private val tenant: TenantContext,
) {
    /** IDX → 이름. 찾은 이름만 담는다. */
    private val names: MutableMap<Long, String> = ConcurrentHashMap()

    /**
     * 현재 사용자가 볼 수 있는 고객사 목록. SUPER 는 전체, COMPANY 는 자기 고객사 1건.
     * 전체 목록을 그대로 돌려주면 COMPANY 화면의 select 에 다른 고객사 이름이 노출된다.
     */
    @Transactional(readOnly = true)
    fun all(): List<CcfaCompany> {
        if (tenant.require().isSuper) {
            return repository.findAll(Sort.by("idx"))
        }
        return repository.findById(tenant.require().companyIdx)
            .map { java.util.List.of(it) }
            .orElseGet { java.util.List.of() }
    }

    /** IDX → 이름. [all] 과 같은 범위 규칙을 따른다. */
    @Transactional(readOnly = true)
    fun names(): Map<Long?, String?> {
        val map = LinkedHashMap<Long?, String?>()
        for (c in all()) map[c.idx] = c.companyName
        return map
    }

    /**
     * 단건 이름. COMPANY 역할이 자기 고객사가 아닌 IDX 를 물으면 이름 대신 "#IDX" 를 준다
     * (존재 여부·이름을 노출하지 않는다).
     *
     * 이름은 사이드바가 요청마다, 감사 로그가 기록마다 묻는다. 거의 바뀌지 않는 값이라 찾은 이름은
     * 기억해 두고 DB 는 처음 한 번만 읽는다. 없는 고객사("#IDX")는 기억하지 않는다 — 나중에 생기면
     * 바로 보여야 한다. 범위 규칙은 캐시 앞에서 적용하므로 캐시가 역할 경계를 넘지 않는다.
     * 고객사 행을 바꾸는 쪽(`CompanyService`)이 [evict] 로 지운다.
     */
    @Transactional(readOnly = true)
    fun name(idx: Long?): String {
        if (idx == null) return ""
        if (!tenant.require().isSuper && idx != tenant.require().companyIdx) return "#$idx"
        val cached = names[idx]
        if (cached != null) return cached
        val found: Optional<String> = repository.findById(idx).map<String> { it.companyName }
        found.ifPresent { name -> names[idx] = name }
        return found.orElse("#$idx")
    }

    /** 고객사 행이 바뀌었다(등록·수정·삭제). 다음 [name] 이 DB 를 다시 읽는다. */
    fun evict(idx: Long?) {
        if (idx != null) names.remove(idx)
    }
}
