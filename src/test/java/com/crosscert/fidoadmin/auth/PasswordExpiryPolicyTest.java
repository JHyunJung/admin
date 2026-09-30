package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class PasswordExpiryPolicyTest {

    static final ZoneId Z = ZoneId.of("Asia/Seoul");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 12, 0);
    Clock clock = Clock.fixed(NOW.atZone(Z).toInstant(), Z);
    PasswordAgeStore store = mock(PasswordAgeStore.class);
    CcfaSystemPropRepository props = mock(CcfaSystemPropRepository.class);

    private PasswordExpiryPolicy policy(String mode) { return new PasswordExpiryPolicy(store, props, mode, clock); }

    @Test void autoEnablesWhenColumnExists() {
        when(store.columnExists()).thenReturn(true);
        assertThat(policy("auto").enabled()).isTrue();
    }

    @Test void autoDisablesWhenColumnMissing() {
        when(store.columnExists()).thenReturn(false);
        PasswordExpiryPolicy p = policy("auto");
        assertThat(p.enabled()).isFalse();
        assertThat(p.isExpiredOnLogin("u")).isFalse();
        p.touch("u");
        verify(store, never()).lastChanged(any());
        verify(store, never()).touch(any());
    }

    @Test void falseDisablesWithoutProbing() {
        PasswordExpiryPolicy p = policy("false");
        assertThat(p.enabled()).isFalse();
        assertThat(p.isExpiredOnLogin("u")).isFalse();
        verify(store, never()).columnExists();
    }

    @Test void probeIsLazyAndMemoized() {
        when(store.columnExists()).thenReturn(true);
        PasswordExpiryPolicy p = policy("auto");
        verify(store, never()).columnExists();
        assertThat(p.enabled()).isTrue();
        assertThat(p.enabled()).isTrue();
        verify(store, times(1)).columnExists();
    }

    @Test void transientProbeFailureIsNotMemoized() {
        when(store.columnExists())
            .thenThrow(new DataAccessResourceFailureException("down"))
            .thenReturn(true);
        PasswordExpiryPolicy p = policy("auto");
        assertThat(p.enabled()).isFalse();
        assertThat(p.enabled()).isTrue();
        verify(store, times(2)).columnExists();
    }

    @Test void expiryBoundaryAt90Days() {
        when(store.columnExists()).thenReturn(true);
        PasswordExpiryPolicy p = policy("auto");
        when(store.lastChanged("u89")).thenReturn(Optional.of(NOW.minusDays(89)));
        when(store.lastChanged("u90")).thenReturn(Optional.of(NOW.minusDays(90)));
        when(store.lastChanged("u91")).thenReturn(Optional.of(NOW.minusDays(91)));
        assertThat(p.isExpiredOnLogin("u89")).isFalse();
        assertThat(p.isExpiredOnLogin("u90")).isTrue();
        assertThat(p.isExpiredOnLogin("u91")).isTrue();
    }

    @Test void nullDateIsNotExpiredAndGetsTouched() {
        when(store.columnExists()).thenReturn(true);
        when(store.lastChanged("legacy")).thenReturn(Optional.empty());
        assertThat(policy("auto").isExpiredOnLogin("legacy")).isFalse();
        verify(store).touch("legacy");
    }

    @Test void expiryDaysFromSystemProp() {
        when(store.columnExists()).thenReturn(true);
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId("PW_EXPIRY_DAYS", 0L));
        p.setPropValue(" 30 ");
        when(props.findById(new CcfaSystemPropId("PW_EXPIRY_DAYS", 0L))).thenReturn(Optional.of(p));
        PasswordExpiryPolicy policy = policy("auto");
        assertThat(policy.expiryDays()).isEqualTo(30);
        when(store.lastChanged("u")).thenReturn(Optional.of(NOW.minusDays(30)));
        assertThat(policy.isExpiredOnLogin("u")).isTrue();
    }

    @Test void invalidOrNonPositiveExpiryDaysFallsBackTo90() {
        when(store.columnExists()).thenReturn(true);
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId("PW_EXPIRY_DAYS", 0L));
        p.setPropValue("0");
        when(props.findById(new CcfaSystemPropId("PW_EXPIRY_DAYS", 0L))).thenReturn(Optional.of(p));
        assertThat(policy("auto").expiryDays()).isEqualTo(90);
        p.setPropValue("abc");
        assertThat(policy("auto").expiryDays()).isEqualTo(90);
    }

    @Test void touchDelegatesWhenEnabled() {
        when(store.columnExists()).thenReturn(true);
        policy("auto").touch("u");
        verify(store).touch("u");
    }

    @Test void storeReportsMissingColumnOnBadSqlGrammar() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new org.springframework.jdbc.BadSqlGrammarException("probe", "SELECT", new java.sql.SQLException("ORA-00904")));
        assertThat(new PasswordAgeStore(jdbc).columnExists()).isFalse();
    }

    @Test void storeReportsColumnWhenProbeSucceeds() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of());
        assertThat(new PasswordAgeStore(jdbc).columnExists()).isTrue();
    }

    @Test void storePropagatesOtherDataAccessExceptions() {
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new DataAccessResourceFailureException("down"));
        assertThatThrownBy(() -> new PasswordAgeStore(jdbc).columnExists())
            .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
