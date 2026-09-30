package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PasswordChangeServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    PasswordExpiryPolicy expiry = mock(PasswordExpiryPolicy.class);
    PasswordChangeService service = new PasswordChangeService(managers, new Sha256PasswordEncoder(), mock(AuditLogger.class), expiry);

    private CcfaManager manager() {
        CcfaManager m = new CcfaManager();
        m.setUserId("kbadmin");
        m.setUserPw("c9d4b06722e867564a14b87c43c62c620f10023d4adeabcea4728d915196c461"); // Company1234!
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(m));
        return m;
    }

    @Test void changesHashWhenCurrentMatches() {
        CcfaManager m = manager();
        service.change("kbadmin", "Company1234!", "NewPass5678!");
        assertThat(m.getUserPw()).isEqualTo(Sha256PasswordEncoder.sha256Hex("NewPass5678!"));
        assertThat(m.getUpdatedtime()).isNotNull();
        verify(expiry).touch("kbadmin");
    }

    @Test void rejectsSameAsCurrent() {
        CcfaManager m = manager();
        assertThatThrownBy(() -> service.change("kbadmin", "Company1234!", "Company1234!"))
            .isInstanceOf(SamePasswordException.class).hasMessageContaining("다른 비밀번호");
        verify(managers, never()).save(any());
        verify(expiry, never()).touch(any());
        assertThat(m.getUserPw()).isEqualTo("c9d4b06722e867564a14b87c43c62c620f10023d4adeabcea4728d915196c461");
    }

    @Test void rejectsWrongCurrent() {
        manager();
        assertThatThrownBy(() -> service.change("kbadmin", "wrong", "NewPass5678!"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("현재 비밀번호");
        verify(expiry, never()).touch(any());
    }
}
