package com.crosscert.fidoadmin.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.entity.CcfaOptions;
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository;
import com.crosscert.fidoadmin.system.repository.CcfaOptionsRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class OptionServiceTest {

    CcfaOptionRepository groups = mock(CcfaOptionRepository.class);
    CcfaOptionsRepository items = mock(CcfaOptionsRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    TenantContext tenant = new TenantContext(new SelectedTenant());
    OptionService service = new OptionService(groups, audit, items, tenant);

    @BeforeEach void loginSuper() { login(0L); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaOption group(long idx) { CcfaOption g = new CcfaOption(); g.setIdx(idx); g.setOptionName("STATUS"); return g; }
    private CcfaOptions item(long idx, long optionIdx) { CcfaOptions i = new CcfaOptions(); i.setIdx(idx); i.setOptionIdx(optionIdx); i.setOptionValue("use"); return i; }

    @Test void addItemAssignsGroupAndAudits() {
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(items.save(any())).thenAnswer(inv -> { CcfaOptions i = inv.getArgument(0); i.setIdx(5L); return i; });
        CcfaOptions in = new CcfaOptions(); in.setOptionValue("unuse"); in.setOptionIdx(999L); // 폼이 무엇을 보내든 경로의 그룹이 이긴다
        CcfaOptions out = service.addItem(1L, in);
        assertThat(out.getOptionIdx()).isEqualTo(1L);
        verify(audit).log(AuditType.CREATE, "CCFA_OPTIONS CREATE 5");
    }

    @Test void addItemToMissingGroupFails() {
        when(groups.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addItem(9L, new CcfaOptions())).isInstanceOf(EntityNotFoundException.class);
        verify(items, never()).save(any());
    }

    @Test void removeItemDeletesAndAudits() {
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(items.findById(5L)).thenReturn(Optional.of(item(5L, 1L)));
        service.removeItem(1L, 5L);
        verify(items).delete(any(CcfaOptions.class));
        verify(audit).log(AuditType.DELETE, "CCFA_OPTIONS DELETE 5");
    }

    /** 다른 그룹의 코드를 경로만 바꿔 지우지 못한다. */
    @Test void removeItemOfOtherGroupIsRejected() {
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(items.findById(5L)).thenReturn(Optional.of(item(5L, 2L)));
        assertThatThrownBy(() -> service.removeItem(1L, 5L)).isInstanceOf(EntityNotFoundException.class);
        verify(items, never()).delete(any(CcfaOptions.class));
        verify(audit, never()).log(any(), any());
    }

    @Test void deleteGroupBlockedWhenItemsExist() {
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(items.countByOptionIdx(1L)).thenReturn(2L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("코드 2건");
        verify(groups, never()).delete(any(CcfaOption.class));
    }

    @Test void deleteEmptyGroupSucceeds() {
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(items.countByOptionIdx(1L)).thenReturn(0L);
        service.delete(1L);
        verify(groups).delete(any(CcfaOption.class));
        verify(audit).log(AuditType.DELETE, "CCFA_OPTION DELETE 1");
    }

    @Test void itemsAreListedInIdxOrder() {
        when(items.findByOptionIdxOrderByIdxAsc(1L)).thenReturn(List.of(item(1L, 1L), item(2L, 1L)));
        assertThat(service.items(1L)).extracting(CcfaOptions::getIdx).containsExactly(1L, 2L);
    }

    @Test void companyRoleIsDeniedForItemsToo() {
        login(1L);
        assertThatThrownBy(() -> service.items(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.addItem(1L, new CcfaOptions())).isInstanceOf(AccessDeniedException.class);
    }
}
