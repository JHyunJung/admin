package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import java.sql.SQLException
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate

class PasswordExpiryPolicyTest {

    companion object {
        val Z: ZoneId = ZoneId.of("Asia/Seoul")
        val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 1, 12, 0)
    }

    val clock: Clock = Clock.fixed(NOW.atZone(Z).toInstant(), Z)
    val store: PasswordAgeStore = mock(PasswordAgeStore::class.java)
    val props: CcfaSystemPropRepository = mock(CcfaSystemPropRepository::class.java)

    private fun policy(mode: String): PasswordExpiryPolicy = PasswordExpiryPolicy(store, props, mode, clock)

    @Test fun autoEnablesWhenColumnExists() {
        whenever(store.columnExists()).thenReturn(true)
        assertThat(policy("auto").enabled()).isTrue()
    }

    @Test fun autoDisablesWhenColumnMissing() {
        whenever(store.columnExists()).thenReturn(false)
        val p = policy("auto")
        assertThat(p.enabled()).isFalse()
        assertThat(p.isExpiredOnLogin("u")).isFalse()
        p.touch("u")
        verify(store, never()).lastChanged(anyOrNull())
        verify(store, never()).touch(anyOrNull())
    }

    @Test fun falseDisablesWithoutProbing() {
        val p = policy("false")
        assertThat(p.enabled()).isFalse()
        assertThat(p.isExpiredOnLogin("u")).isFalse()
        verify(store, never()).columnExists()
    }

    @Test fun probeIsLazyAndMemoized() {
        whenever(store.columnExists()).thenReturn(true)
        val p = policy("auto")
        verify(store, never()).columnExists()
        assertThat(p.enabled()).isTrue()
        assertThat(p.enabled()).isTrue()
        verify(store, times(1)).columnExists()
    }

    @Test fun transientProbeFailureIsNotMemoized() {
        whenever(store.columnExists())
            .thenThrow(DataAccessResourceFailureException("down"))
            .thenReturn(true)
        val p = policy("auto")
        assertThat(p.enabled()).isFalse()
        assertThat(p.enabled()).isTrue()
        verify(store, times(2)).columnExists()
    }

    @Test fun expiryBoundaryAt90Days() {
        whenever(store.columnExists()).thenReturn(true)
        val p = policy("auto")
        whenever(store.lastChanged("u89")).thenReturn(Optional.of(NOW.minusDays(89)))
        whenever(store.lastChanged("u90")).thenReturn(Optional.of(NOW.minusDays(90)))
        whenever(store.lastChanged("u91")).thenReturn(Optional.of(NOW.minusDays(91)))
        assertThat(p.isExpiredOnLogin("u89")).isFalse()
        assertThat(p.isExpiredOnLogin("u90")).isTrue()
        assertThat(p.isExpiredOnLogin("u91")).isTrue()
    }

    @Test fun nullDateIsNotExpiredAndGetsTouched() {
        whenever(store.columnExists()).thenReturn(true)
        whenever(store.lastChanged("norecord")).thenReturn(Optional.empty())
        assertThat(policy("auto").isExpiredOnLogin("norecord")).isFalse()
        verify(store).touch("norecord")
    }

    @Test fun expiryDaysFromSystemProp() {
        whenever(store.columnExists()).thenReturn(true)
        val p = CcfaSystemProp()
        p.id = CcfaSystemPropId("PW_EXPIRY_DAYS", 0L)
        p.propValue = " 30 "
        whenever(props.findById(CcfaSystemPropId("PW_EXPIRY_DAYS", 0L))).thenReturn(Optional.of(p))
        val policy = policy("auto")
        assertThat(policy.expiryDays()).isEqualTo(30)
        whenever(store.lastChanged("u")).thenReturn(Optional.of(NOW.minusDays(30)))
        assertThat(policy.isExpiredOnLogin("u")).isTrue()
    }

    @Test fun invalidOrNonPositiveExpiryDaysFallsBackTo90() {
        whenever(store.columnExists()).thenReturn(true)
        val p = CcfaSystemProp()
        p.id = CcfaSystemPropId("PW_EXPIRY_DAYS", 0L)
        p.propValue = "0"
        whenever(props.findById(CcfaSystemPropId("PW_EXPIRY_DAYS", 0L))).thenReturn(Optional.of(p))
        assertThat(policy("auto").expiryDays()).isEqualTo(90)
        p.propValue = "abc"
        assertThat(policy("auto").expiryDays()).isEqualTo(90)
    }

    @Test fun touchDelegatesWhenEnabled() {
        whenever(store.columnExists()).thenReturn(true)
        policy("auto").touch("u")
        verify(store).touch("u")
    }

    @Test fun storeReportsMissingColumnOnBadSqlGrammar() {
        val jdbc = mock(JdbcTemplate::class.java)
        whenever(jdbc.queryForList(anyString()))
            .thenThrow(BadSqlGrammarException("probe", "SELECT", SQLException("ORA-00904")))
        assertThat(PasswordAgeStore(jdbc).columnExists()).isFalse()
    }

    @Test fun storeReportsColumnWhenProbeSucceeds() {
        val jdbc = mock(JdbcTemplate::class.java)
        whenever(jdbc.queryForList(anyString())).thenReturn(listOf())
        assertThat(PasswordAgeStore(jdbc).columnExists()).isTrue()
    }

    @Test fun storePropagatesOtherDataAccessExceptions() {
        val jdbc = mock(JdbcTemplate::class.java)
        whenever(jdbc.queryForList(anyString()))
            .thenThrow(DataAccessResourceFailureException("down"))
        assertThatThrownBy { PasswordAgeStore(jdbc).columnExists() }
            .isInstanceOf(DataAccessResourceFailureException::class.java)
    }
}
