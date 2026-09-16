package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
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

    @Transactional(readOnly = true)
    public List<CcfaCompany> all() {
        return repository.findAll(Sort.by("idx"));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> names() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (CcfaCompany c : all()) map.put(c.getIdx(), c.getCompanyName());
        return map;
    }

    @Transactional(readOnly = true)
    public String name(Long idx) {
        if (idx == null) return "";
        return repository.findById(idx).map(CcfaCompany::getCompanyName).orElse("#" + idx);
    }
}
