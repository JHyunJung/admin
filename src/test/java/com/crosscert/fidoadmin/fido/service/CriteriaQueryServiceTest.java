package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.repository.CriteriaRepository;
import com.crosscert.fidoadmin.fido.web.CriteriaSearchForm;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class CriteriaQueryServiceTest {

    CriteriaRepository repo = mock(CriteriaRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    CriteriaQueryService service =
        new CriteriaQueryService(repo, audit, new TenantContext(new SelectedTenant()), jdbc);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private Criteria criteria(long idx, String aaid) {
        Criteria c = new Criteria();
        c.setIdx(idx);
        c.setAaid(aaid);
        return c;
    }

    /**
     * CRITERIA 는 COMPANY_IDX 가 없는 전역 테이블이지만 이 화면은 SUPER 전용이 아니다.
     * 화면의 용도가 고객사별 토글(CCFA_COMPANY_AAID)이라 COMPANY 운영자가 쓴다.
     */
    @Test void companyRoleCanSearchAndGet() {
        login(1L);
        when(repo.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        when(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")));

        service.search(new CriteriaSearchForm(), PageRequest.of(0, 20, service.defaultSort()));

        assertThat(service.get(1L).getAaid()).isEqualTo("0012#0001");
        verify(repo).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test void superRoleCanSearchAndGet() {
        login(0L);
        when(repo.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        when(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")));
        CriteriaSearchForm f = new CriteriaSearchForm();
        f.setAaid("0012");

        service.search(f, PageRequest.of(0, 20, service.defaultSort()));

        assertThat(service.get(1L).getAaid()).isEqualTo("0012#0001");
        verify(repo).findAll(any(Specification.class), any(PageRequest.class));
    }

    /** 로그인하지 않았으면 열리지 않는다. 전역 차단을 푼 뒤에도 이것은 남는다. */
    @Test void anonymousIsRejected() {
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(IllegalStateException.class);
        verify(repo, never()).findById(any());
    }

    /** 꺼 둔 AAID 조회는 유효 테넌트로 필터한다 — 남의 고객사 설정이 섞이면 안 된다. */
    @Test void disabledAaidsFiltersByEffectiveTenant() {
        login(7L);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
            .thenReturn(List.of("0012#0001"));

        assertThat(service.disabledAaids()).containsExactly("0012#0001");

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(anyString(), params.capture(), eq(String.class));
        assertThat(params.getValue().getValue("companyIdx")).isEqualTo(7L);
    }

    /** 비활성화는 차단 목록에 행을 넣는다(레거시 disableCompanyAAID 와 같은 규약). */
    @Test void disablingInsertsBlockRowAndLogs() {
        login(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(7L);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        assertThat(service.changeStatus(1L, false)).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(MapSqlParameterSource.class));
        assertThat(sql.getValue()).contains("INSERT INTO CCFA_COMPANY_AAID");
        verify(audit).log(eq(AuditType.STATUS), anyString());
    }

    /** 활성화는 차단 목록에서 행을 지운다(레거시 enableCompanyAAID). */
    @Test void enablingDeletesBlockRow() {
        login(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(7L);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        assertThat(service.changeStatus(1L, true)).isTrue();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(MapSqlParameterSource.class));
        assertThat(sql.getValue()).contains("DELETE FROM CCFA_COMPANY_AAID");
    }

    /** 이미 그 상태면 false 를 주고 감사 로그를 남기지 않는다 — 바뀐 것이 없다. */
    @Test void noChangeReportsFalseAndSkipsAudit() {
        login(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "0012#0001")));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(7L);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);

        assertThat(service.changeStatus(1L, true)).isFalse();

        verify(audit, never()).log(any(AuditType.class), anyString());
    }

    /** AAID 가 없는 행은 켜고 끌 대상이 없다. 차단 목록에 빈 값을 넣지 않는다. */
    @Test void blankAaidIsRejected() {
        login(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(criteria(1L, "  ")));

        assertThatThrownBy(() -> service.changeStatus(1L, false))
            .isInstanceOf(ResponseStatusException.class);

        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test void defaultSortIsIdxDesc() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }

    @Test void sortablePropertiesIncludeAaidAndUpdatedtime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "aaid", "updatedtime");
    }
}
