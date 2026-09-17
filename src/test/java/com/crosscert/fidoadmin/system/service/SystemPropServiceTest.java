package com.crosscert.fidoadmin.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SystemPropServiceTest {

    CcfaSystemPropRepository repo = mock(CcfaSystemPropRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    EntityManager em = mock(EntityManager.class);
    SystemPropService service = new SystemPropService(repo, audit, em);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(em.createNativeQuery(anyString())).thenReturn(mock(Query.class));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaSystemProp prop(String key, long company) {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId(key, company));
        p.setPropValue("v");
        return p;
    }

    /** 같은 (PROP_KEY, COMPANY_IDX) 가 있으면 MERGE 로 덮어쓰지 않고 거부한다. */
    @Test void createRejectsDuplicateCompositeKey() {
        when(repo.existsById(new CcfaSystemPropId("PW_FAIL_LIMIT", 0L))).thenReturn(true);
        assertThatThrownBy(() -> service.create(prop("PW_FAIL_LIMIT", 0L)))
            .isInstanceOf(DataIntegrityViolationException.class);
        verify(em, never()).persist(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void createPersistsWithDefaultsAndAudits() {
        when(repo.existsById(any())).thenReturn(false);
        CcfaSystemProp saved = service.create(prop("PW_FAIL_LIMIT", 0L));
        assertThat(saved.getShareType()).isEqualTo("NO");
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(em).persist(saved);
        verify(em).flush();
        verify(audit).log(AuditType.CREATE, "CCFA_SYSTEM_PROP CREATE PW_FAIL_LIMIT@0");
    }

    @Test void idOfIsPathValue() {
        assertThat(service.idOf(prop("SESSION_TIMEOUT", 0L))).isEqualTo("SESSION_TIMEOUT@0");
    }

    @Test void defaultSortIsByKeyThenCompany() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id.propKey", "id.companyIdx"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("id.propKey", "id.companyIdx", "updatedtime");
    }
}
