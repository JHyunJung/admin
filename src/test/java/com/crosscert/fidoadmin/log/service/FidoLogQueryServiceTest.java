package com.crosscert.fidoadmin.log.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import com.crosscert.fidoadmin.log.repository.FidoLogsRepository;
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
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

class FidoLogQueryServiceTest {

    FidoLogsRepository repo = mock(FidoLogsRepository.class);
    FidoLogQueryService service = new FidoLogQueryService(repo, mock(AuditLogger.class), new TenantContext(new SelectedTenant()));

    @BeforeEach void loginCompany() {
        var u = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void logsSortByCreatedtimeThenIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdtime", "idx"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createdtime", "servicename");
    }

    @Test void tenantAttributeIsCompanyIdx() {
        assertThat(service.companyIdxAttribute()).isEqualTo("companyIdx");
        FidoLogs l = new FidoLogs(); l.setCompanyIdx(7L);
        assertThat(service.companyIdxOf(l)).isEqualTo(7L);
    }

    /** 기간 조건이 createdtime 에, 서비스명·시리얼이 like 로 걸리는지 Specification 을 실제로 평가해 확인한다. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void specificationUsesCreatedtimeAndLikeColumns() {
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        FidoLogSearchForm f = new FidoLogSearchForm();
        f.setServicename("kbstar"); f.setSerialcode("SN-");
        f.setFromDate(LocalDate.of(2026, 9, 1)); f.setToDate(LocalDate.of(2026, 9, 16));

        service.search(f, PageRequest.of(0, 20, service.defaultSort()));

        ArgumentCaptor<Specification<FidoLogs>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));
        Root<FidoLogs> root = mock(Root.class, Mockito.RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, Mockito.RETURNS_DEEP_STUBS);
        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(root, Mockito.atLeastOnce()).get("createdtime");
        verify(root).get("servicename");
        verify(root).get("serialcode");
        verify(root).get("companyIdx"); // COMPANY 역할의 테넌트 필터
    }
}
