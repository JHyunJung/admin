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
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
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
    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);
    SystemPropService service = new SystemPropService(repo, audit, em, tenant);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        when(em.createNativeQuery(anyString())).thenReturn(mock(Query.class));
        selected.select(1L); // create() 가 id.companyIdx 를 이 값으로 덮어쓴다(설계: 유효 테넌트가 소유자를 정한다)
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaSystemProp prop(String key, long company) {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId(key, company));
        p.setPropValue("v");
        return p;
    }

    /**
     * 같은 (PROP_KEY, COMPANY_IDX) 가 있으면 MERGE 로 덮어쓰지 않고 거부한다.
     *
     * <p>{@code SystemPropService.setCompanyIdx()} 는 EmbeddedId 안에 값을 쓰고
     * {@code assignedId()} 가 그 id 를 그대로 읽으므로, {@code CrudService.create()} 가
     * {@code setCompanyIdx()} → {@code insert()} 순으로 부르는 이상 {@code existsById(assignedId(e))}
     * 는 폼의 companyIdx 가 아니라 <b>유효 테넌트가 박힌 키</b>로 조회한다. 스텁을 선택 테넌트(1)에 맞춘다.
     */
    @Test void createRejectsDuplicateCompositeKey() {
        when(repo.existsById(new CcfaSystemPropId("PW_FAIL_LIMIT", 1L))).thenReturn(true);
        assertThatThrownBy(() -> service.create(prop("PW_FAIL_LIMIT", 0L)))
            .isInstanceOf(DataIntegrityViolationException.class);
        verify(em, never()).persist(any());
        verify(audit, never()).log(any(), any());
    }

    /**
     * 이름은 중립적이었지만 원래 감사 로그 단언이 "PW_FAIL_LIMIT@0" 을 기대했다 — 즉 폼에 넣은
     * companyIdx(0) 가 결과 키에 그대로 남는지를 검증하고 있었다. 새 모델에서는 유효 테넌트가
     * companyIdx 를 정하므로(설계: Task 3), 저장된 id.companyIdx 가 선택 테넌트(1)인지로
     * 단언을 바꾼다. 기본값·타임스탬프·감사 호출 검증은 그대로 둔다.
     */
    @Test void createPersistsWithSelectedTenantAsCompanyIdx() {
        when(repo.existsById(any())).thenReturn(false);
        CcfaSystemProp saved = service.create(prop("PW_FAIL_LIMIT", 0L));
        assertThat(saved.getId().getCompanyIdx()).as("유효 테넌트가 companyIdx 를 정한다").isEqualTo(1L);
        assertThat(saved.getShareType()).isEqualTo("NO");
        assertThat(saved.getUpdatedtime()).isNotNull();
        verify(em).persist(saved);
        verify(em).flush();
        verify(audit).log(AuditType.CREATE, "CCFA_SYSTEM_PROP CREATE PW_FAIL_LIMIT@1");
    }

    @Test void idOfIsPathValue() {
        assertThat(service.idOf(prop("SESSION_TIMEOUT", 0L))).isEqualTo("SESSION_TIMEOUT@0");
    }

    @Test void defaultSortIsByKeyThenCompany() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id.propKey", "id.companyIdx"));
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("id.propKey", "id.companyIdx", "updatedtime");
    }
}
