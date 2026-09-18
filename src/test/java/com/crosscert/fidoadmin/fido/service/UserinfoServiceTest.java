package com.crosscert.fidoadmin.fido.service;

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
import com.crosscert.fidoadmin.common.TenantMismatchException;
import com.crosscert.fidoadmin.fido.entity.Userinfo;
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class UserinfoServiceTest {

    UserinfoRepository repo = mock(UserinfoRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);
    UserinfoService service = new UserinfoService(repo, audit, tenant);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private Userinfo user(long idx, long companyIdx, String status) {
        Userinfo u = new Userinfo(); u.setIdx(idx); u.setCompanyIdx(companyIdx); u.setUserid("user001");
        u.setStatus(status); u.setPubkey("PUBKEY"); u.setCertificate("CERT");
        return u;
    }

    @Test void changeStatusSavesAndAuditsOldToNew() {
        login(1L);
        Userinfo existing = user(5L, 1L, "O");
        when(repo.findById(5L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Userinfo out = service.changeStatus(5L, "X");

        assertThat(out.getStatus()).isEqualTo("X");
        verify(repo).save(existing);
        verify(audit).log(AuditType.STATUS, "USERINFO STATUS 5 O->X");
    }

    @Test void changeStatusRejectsUnknownValueBeforeTouchingRepository() {
        login(1L);
        assertThatThrownBy(() -> service.changeStatus(5L, "Z"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Z");
        verify(repo, never()).findById(any());
        verify(repo, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    @Test void changeStatusRejectsNull() {
        login(1L);
        assertThatThrownBy(() -> service.changeStatus(5L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 상태 변경도 테넌트 검사를 거친다. 다른 고객사의 사용자는 404(TenantMismatchException). */
    @Test void companyRoleCannotChangeOtherTenantStatus() {
        login(1L);
        when(repo.findById(9L)).thenReturn(Optional.of(user(9L, 2L, "O")));
        assertThatThrownBy(() -> service.changeStatus(9L, "X")).isInstanceOf(TenantMismatchException.class);
        verify(repo, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    /**
     * Task 3 가 막은 구멍: SUPER 가 미선택 상태로건, 선택한 것과 다른 테넌트의 행이건
     * URL 로 상태를 바꿀 수 있어서는 안 된다. 소유(2)와 다른 테넌트(9)를 선택하면
     * TenantMismatchException 이어야 한다(404 로 처리됨, CrudService.checkTenant 참고).
     */
    @Test void superCannotChangeOtherTenantStatus() {
        login(0L);
        selected.select(9L);
        when(repo.findById(9L)).thenReturn(Optional.of(user(9L, 2L, "X")));
        assertThatThrownBy(() -> service.changeStatus(9L, "O")).isInstanceOf(TenantMismatchException.class);
        verify(repo, never()).save(any());
        verify(audit, never()).log(any(), any());
    }

    /** SUPER 가 자신이 선택한 테넌트의 행이라면 상태를 바꿀 수 있다(감사 로그 포함). */
    @Test void superChangesSelectedTenantStatus() {
        login(0L);
        selected.select(2L);
        when(repo.findById(9L)).thenReturn(Optional.of(user(9L, 2L, "X")));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.changeStatus(9L, "O").getStatus()).isEqualTo("O");
        verify(audit).log(AuditType.STATUS, "USERINFO STATUS 9 X->O");
    }

    @Test void sortablePropertiesCoverListColumns() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "userid", "servicename", "regtime");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }
}
