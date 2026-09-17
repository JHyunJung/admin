package com.crosscert.fidoadmin.log.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.log.entity.CcfaExceptions;
import com.crosscert.fidoadmin.log.repository.CcfaExceptionsRepository;
import com.crosscert.fidoadmin.log.web.ExceptionLogSearchForm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ExceptionLogQueryServiceTest {

    CcfaExceptionsRepository repo = mock(CcfaExceptionsRepository.class);
    ExceptionLogQueryService service = new ExceptionLogQueryService(repo, mock(AuditLogger.class));

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createdtime", "eType", "eLevel");
        assertThat(service.companyIdxAttribute()).isEqualTo("companyIdx");
    }

    /**
     * CREATEDTIME 은 VARCHAR2 라 기간(between) 이 아니라 문자열 like 로 검색한다(설계 5.2).
     * 실수로 base fromDate 를 between 에 넣으면 문자열 컬럼 비교로 조회가 깨지므로,
     * Specification 이 createdtime 을 like 경로로만 쓰는지 확인한다.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void createdtimeIsSearchedAsStringLike() {
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        ExceptionLogSearchForm f = new ExceptionLogSearchForm();
        f.setEType("AUTH"); f.setELevel("ERROR"); f.setMessage("signature"); f.setCreatedtime("2026-09-15");

        service.search(f, PageRequest.of(0, 20, service.defaultSort()));

        ArgumentCaptor<Specification<CcfaExceptions>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));
        Root<CcfaExceptions> root = mock(Root.class, Mockito.RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, Mockito.RETURNS_DEEP_STUBS);
        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(root).get("eType");
        verify(root).get("eLevel");
        verify(root).get("exceptionMessage");
        verify(root).get("createdtime");
        verify(cb, never()).greaterThanOrEqualTo(any(), (java.time.LocalDateTime) any());
        verify(cb, never()).lessThan(any(), (java.time.LocalDateTime) any());
    }
}
