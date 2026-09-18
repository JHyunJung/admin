package com.crosscert.fidoadmin.fido2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;
import com.crosscert.fidoadmin.fido2.repository.Fido2CredentialParamsRepository;
import com.crosscert.fidoadmin.fido2.web.Fido2CredentialParamsSearchForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class Fido2CredentialParamsServiceTest {

    Fido2CredentialParamsRepository repo = mock(Fido2CredentialParamsRepository.class);
    Fido2CredentialParamsService service = new Fido2CredentialParamsService(repo, mock(AuditLogger.class), new TenantContext(new SelectedTenant()));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test void defaultsFilledOnCreate() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Fido2CredentialParams p = new Fido2CredentialParams();
        p.setCredAlg(-7L);
        Fido2CredentialParams saved = service.create(p);
        assertThat(saved.getCredType()).isEqualTo("public-key");
        assertThat(saved.getStatus()).isEqualTo("T");
        assertThat(saved.getCreatedtime()).isNotNull();
    }

    @Test void explicitValuesAreKept() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Fido2CredentialParams p = new Fido2CredentialParams();
        p.setCredType("custom"); p.setCredAlg(-257L); p.setStatus("F");
        Fido2CredentialParams saved = service.create(p);
        assertThat(saved.getCredType()).isEqualTo("custom");
        assertThat(saved.getStatus()).isEqualTo("F");
    }

    @Test void companyRoleCannotSearch() {
        login(1L);
        assertThatThrownBy(() -> service.search(new Fido2CredentialParamsSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))))
            .isInstanceOf(AccessDeniedException.class);
    }
}
