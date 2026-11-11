package com.crosscert.fidoadmin.auth

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

/**
 * 같은 계정의 로그인 시도를 직렬화한다(진단 78). 잠금 판정·비밀번호 대조·실패 집계가 한 트랜잭션 안에서
 * 계정 행 잠금을 쥔 채 일어나야, 동시에 N건을 보내도 한도(5회)를 넘겨 대조하지 못한다.
 */
class SerializedLoginAuthenticationProviderTest {

    /** 커밋·롤백 횟수와 지금 트랜잭션 안인지를 기록한다. */
    class RecordingTm : AbstractPlatformTransactionManager() {
        var commits = 0; var rollbacks = 0; var active = false
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) { active = true }
        override fun doCommit(status: DefaultTransactionStatus) { commits++; active = false }
        override fun doRollback(status: DefaultTransactionStatus) { rollbacks++; active = false }
    }

    val users: UserDetailsService = mock(UserDetailsService::class.java)
    val attempts: LoginAttemptService = mock(LoginAttemptService::class.java)
    val tm = RecordingTm()
    val provider = SerializedLoginAuthenticationProvider(users, Sha256PasswordEncoder(), attempts, TransactionTemplate(tm))

    private fun user(locked: Boolean = false) =
        ManagerUserDetails(2L, "kbadmin", Sha256PasswordEncoder.sha256Hex("Right1234!"), "운영자", 1L, "고객사", true, !locked)

    @Test fun locksAccountRowBeforeLoadingUser() {
        whenever(users.loadUserByUsername("kbadmin")).thenReturn(user())
        provider.authenticate(UsernamePasswordAuthenticationToken("kbadmin", "Right1234!"))
        val order = inOrder(attempts, users)
        order.verify(attempts).lockForAttempt("kbadmin")
        order.verify(users).loadUserByUsername("kbadmin")
    }

    /** 실패 집계는 잠금을 쥔 같은 트랜잭션에서 하고, 그 트랜잭션은 커밋된다(롤백되면 집계가 사라진다). */
    @Test fun badPasswordIsCountedInsideCommittedTransaction() {
        whenever(users.loadUserByUsername("kbadmin")).thenReturn(user())
        var countedInTx = false
        doAnswer { countedInTx = tm.active; null }.whenever(attempts).onFailure("kbadmin")

        assertThatThrownBy { provider.authenticate(UsernamePasswordAuthenticationToken("kbadmin", "wrong")) }
            .isInstanceOf(BadCredentialsException::class.java)

        assertThat(countedInTx).isTrue()
        assertThat(tm.commits).isEqualTo(1)
        assertThat(tm.rollbacks).isEqualTo(0)
    }

    /**
     * 선행 잠금 조회가 실패해도(USER_ID 중복 등) 기본 provider 처럼 인증 예외로 바꿔 실패 핸들러(/login?error)로 보낸다.
     * 트랜잭션은 되돌린다.
     */
    @Test fun lockFailureBecomesInternalAuthenticationException() {
        whenever(attempts.lockForAttempt("kbadmin"))
            .thenThrow(org.springframework.dao.IncorrectResultSizeDataAccessException(1, 2))
        assertThatThrownBy { provider.authenticate(UsernamePasswordAuthenticationToken("kbadmin", "wrong")) }
            .isInstanceOf(org.springframework.security.authentication.InternalAuthenticationServiceException::class.java)
            .hasCauseInstanceOf(org.springframework.dao.IncorrectResultSizeDataAccessException::class.java)
        assertThat(tm.rollbacks).isEqualTo(1)
        verify(attempts, never()).onFailure(anyOrNull())
    }

    @Test fun successDoesNotCountFailure() {
        whenever(users.loadUserByUsername("kbadmin")).thenReturn(user())
        val result = provider.authenticate(UsernamePasswordAuthenticationToken("kbadmin", "Right1234!"))
        assertThat(result!!.isAuthenticated).isTrue()
        verify(attempts, never()).onFailure(anyOrNull())
    }

    /** 잠긴 계정은 대조 전에 막히고, 지금처럼 실패로 세지 않는다. */
    @Test fun lockedAccountIsRejectedWithoutCounting() {
        whenever(users.loadUserByUsername("kbadmin")).thenReturn(user(locked = true))
        assertThatThrownBy { provider.authenticate(UsernamePasswordAuthenticationToken("kbadmin", "wrong")) }
            .isInstanceOf(LockedException::class.java)
        verify(attempts, never()).onFailure(anyOrNull())
    }
}
