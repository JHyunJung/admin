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
import com.crosscert.fidoadmin.log.entity.CcfaMailing;
import com.crosscert.fidoadmin.log.repository.CcfaMailingRepository;
import com.crosscert.fidoadmin.log.web.MailingSearchForm;
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

class MailingQueryServiceTest {

    CcfaMailingRepository repo = mock(CcfaMailingRepository.class);
    MailingQueryService service = new MailingQueryService(repo, mock(AuditLogger.class), new TenantContext(new SelectedTenant()));

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "status", "sendtime");
        assertThat(service.companyIdxAttribute()).isEqualTo("companyIdx");
    }

    /**
     * 수신자 검색은 예약어 컬럼 "TO" 에 매핑된 엔티티 속성 `to` 를 써야 한다.
     * 속성명을 잘못 적으면(예: "TO", "receiver") 조회 시 500 이 나므로 실제 경로를 확인한다.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void recipientSearchUsesToAttribute() {
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        MailingSearchForm f = new MailingSearchForm();
        f.setStatus("SENT"); f.setTo("ops@"); f.setSmsStatus("SENT");

        service.search(f, PageRequest.of(0, 20, service.defaultSort()));

        ArgumentCaptor<Specification<CcfaMailing>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(captor.capture(), any(Pageable.class));
        Root<CcfaMailing> root = mock(Root.class, Mockito.RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, Mockito.RETURNS_DEEP_STUBS);
        captor.getValue().toPredicate(root, mock(CriteriaQuery.class), cb);
        verify(root).get("to");
        verify(root).get("status");
        verify(root).get("smsStatus");
    }
}
