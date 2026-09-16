package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고객사 IDX → 이름 조회. 화면 표시와 select 옵션에 사용. */
@Service
@RequiredArgsConstructor
public class CompanyLookup {

    private final CcfaCompanyRepository repository;

    /**
     * 현재 사용자가 볼 수 있는 고객사 목록. SUPER 는 전체, COMPANY 는 자기 고객사 1건.
     * 전체 목록을 그대로 돌려주면 COMPANY 화면의 select 에 다른 고객사 이름이 노출된다.
     */
    @Transactional(readOnly = true)
    public List<CcfaCompany> all() {
        if (TenantContext.isSuper()) {
            return repository.findAll(Sort.by("idx"));
        }
        return repository.findById(TenantContext.companyIdx()).map(List::of).orElseGet(List::of);
    }

    /** IDX → 이름. {@link #all()} 과 같은 범위 규칙을 따른다. */
    @Transactional(readOnly = true)
    public Map<Long, String> names() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (CcfaCompany c : all()) map.put(c.getIdx(), c.getCompanyName());
        return map;
    }

    /**
     * 단건 이름. COMPANY 역할이 자기 고객사가 아닌 IDX 를 물으면 이름 대신 "#IDX" 를 준다
     * (존재 여부·이름을 노출하지 않는다).
     */
    @Transactional(readOnly = true)
    public String name(Long idx) {
        if (idx == null) return "";
        if (!TenantContext.isSuper() && !idx.equals(TenantContext.companyIdx())) return "#" + idx;
        return repository.findById(idx).map(CcfaCompany::getCompanyName).orElse("#" + idx);
    }
}
