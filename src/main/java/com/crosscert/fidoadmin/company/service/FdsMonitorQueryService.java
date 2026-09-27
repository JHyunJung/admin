package com.crosscert.fidoadmin.company.service;

import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.repository.CcfaFdsPolicyRepository;
import com.crosscert.fidoadmin.log.service.FidoLogTable;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FDS 모니터링 — 같은 기기(SERIALCODE)의 직전·직후 요청이 N초 이내인 로그를 찾는다.
 *
 * <p>이전 어드민의 {@code fds.FDSMonitor} 를 현재 스키마로 다시 정의한 것이다. 그쪽이 보던
 * IP 대역·기기 유형·국가는 현재 FIDO_LOGS 에 컬럼이 없어 뺐고, 반복 주기만 남겼다.
 * 주체도 IP+사용자(CCFA_FIDO_TLOG, 지금은 없음)에서 SERIALCODE 로 바꿨다.
 * 설계: docs/superpowers/specs/2026-09-27-fds-monitor-design.md
 *
 * <p>{@code CrudService} 를 상속하지 않는다. 날짜별 분할 테이블이라 JPA 엔티티가 없다.
 * 테넌트 경계는 같은 규칙으로 지킨다 — 모든 조회가 {@code COMPANY_IDX = 유효 테넌트} 를 건다.
 */
@Service
@RequiredArgsConstructor
public class FdsMonitorQueryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;
    private final CcfaFdsPolicyRepository policies;
    private final FidoLogTable tables;

    /**
     * 현재 고객사 FDS 정책의 반복 주기(초). 없으면 비어 있다.
     *
     * <p>AND_TERM 과 OR_TERM 중 파싱되는 값을 쓰고, 둘 다 있으면 <b>짧은 쪽</b>이다.
     * 이전 어드민이 두 그룹을 나눈 이유는 IP·국가 조건과 조합하기 위해서였는데, 그 조건들이
     * 없는 지금은 두 그룹이 같은 조건 하나로 줄어 구분이 의미를 잃는다. 하나로 합친다.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> policyTerm() {
        Long companyIdx = tenant.companyIdx();
        return policies.findById(companyIdx)
            .flatMap(p -> shorter(parseTerm(p.getAndTerm()), parseTerm(p.getOrTerm())));
    }

    /** 양의 정수(초)만 값으로 본다. Oracle 은 빈 문자열을 NULL 로 저장하고 운영자가 "30초" 를 넣어 둘 수 있다. */
    static Optional<Integer> parseTerm(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Optional<Integer> shorter(Optional<Integer> a, Optional<Integer> b) {
        if (a.isPresent() && b.isPresent()) return Optional.of(Math.min(a.get(), b.get()));
        return a.isPresent() ? a : b;
    }
}
