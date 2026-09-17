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
    UserinfoService service = new UserinfoService(repo, audit);

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

    @Test void superCanChangeAnyTenantStatus() {
        login(0L);
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
