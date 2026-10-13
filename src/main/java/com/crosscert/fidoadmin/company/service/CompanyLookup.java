package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 고객사 IDX → 이름 조회. 화면 표시와 select 옵션에 사용. */
@Service
@RequiredArgsConstructor
public class CompanyLookup {

    private final CcfaCompanyRepository repository;
    private final TenantContext tenant;
    /** IDX → 이름. 찾은 이름만 담는다. */
    private final Map<Long, String> names = new ConcurrentHashMap<>();

    /**
     * 현재 사용자가 볼 수 있는 고객사 목록. SUPER 는 전체, COMPANY 는 자기 고객사 1건.
     * 전체 목록을 그대로 돌려주면 COMPANY 화면의 select 에 다른 고객사 이름이 노출된다.
     */
    @Transactional(readOnly = true)
    public List<CcfaCompany> all() {
        if (tenant.require().isSuper()) {
            return repository.findAll(Sort.by("idx"));
        }
        return repository.findById(tenant.require().getCompanyIdx()).map(List::of).orElseGet(List::of);
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
     *
     * <p>이름은 사이드바가 요청마다, 감사 로그가 기록마다 묻는다. 거의 바뀌지 않는 값이라 찾은 이름은
     * 기억해 두고 DB 는 처음 한 번만 읽는다. 없는 고객사("#IDX")는 기억하지 않는다 — 나중에 생기면
     * 바로 보여야 한다. 범위 규칙은 캐시 앞에서 적용하므로 캐시가 역할 경계를 넘지 않는다.
     * 고객사 행을 바꾸는 쪽({@code CompanyService})이 {@link #evict} 로 지운다.
     */
    @Transactional(readOnly = true)
    public String name(Long idx) {
        if (idx == null) return "";
        if (!tenant.require().isSuper() && !idx.equals(tenant.require().getCompanyIdx())) return "#" + idx;
        String cached = names.get(idx);
        if (cached != null) return cached;
        Optional<String> found = repository.findById(idx).map(CcfaCompany::getCompanyName);
        found.ifPresent(name -> names.put(idx, name));
        return found.orElse("#" + idx);
    }

    /** 고객사 행이 바뀌었다(등록·수정·삭제). 다음 {@link #name} 이 DB 를 다시 읽는다. */
    public void evict(Long idx) {
        if (idx != null) names.remove(idx);
    }
}
