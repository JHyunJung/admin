package com.crosscert.fidoadmin.fido2.service;

import java.util.Optional;
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged;
import org.springframework.context.ApplicationEventPublisher;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import com.crosscert.fidoadmin.fido2.repository.Fido2MetadataRepository;
import com.crosscert.fidoadmin.fido2.web.Fido2MetadataSearchForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class Fido2MetadataServiceTest {

    Fido2MetadataRepository repo = mock(Fido2MetadataRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    Fido2MetadataService service = new Fido2MetadataService(repo, mock(AuditLogger.class), new TenantContext(new SelectedTenant()), events);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    /** COMPANY_IDX 가 없는 테이블은 SUPER 전용(설계 3.3). 서비스 계층에서도 막힌다. */
    @Test void companyRoleCannotSearch() {
        login(1L);
        assertThatThrownBy(() -> service.search(new Fido2MetadataSearchForm(), PageRequest.of(0, 20, Sort.by("idx"))))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test void createFillsCreatedtime() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Fido2Metadata m = new Fido2Metadata();
        m.setDescription("YubiKey"); m.setAaguid("cb69481e-8ff7-4039-93ec-0a2729a154a8");
        Fido2Metadata saved = service.create(m);
        assertThat(saved.getCreatedtime()).isNotNull();
    }

    @Test void sortableIncludesListColumnsOnly() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "description", "aaguid", "protocolfamily", "createdtime");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }

    @Test void createUpdateDeletePublishReload() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> { Fido2Metadata a = inv.getArgument(0); if (a.getIdx() == null) a.setIdx(12L); return a; });
        Fido2Metadata created = service.create(new Fido2Metadata());
        verify(events).publishEvent(new FidoConfigChanged("FIDO2_METADATA CREATE 12"));

        when(repo.findById(12L)).thenReturn(Optional.of(created));
        service.update(12L, a -> { });
        verify(events).publishEvent(new FidoConfigChanged("FIDO2_METADATA UPDATE 12"));

        service.delete(12L);
        verify(events).publishEvent(new FidoConfigChanged("FIDO2_METADATA DELETE 12"));
    }
}
