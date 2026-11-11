package com.crosscert.fidoadmin.auth

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate

/**
 * 폼 로그인 인증. 기본 [DaoAuthenticationProvider] 와 같되, 같은 계정의 시도를 한 줄로 세운다.
 *
 * 기본 흐름은 잠금 판정(잠금 없는 읽기) → 비밀번호 대조 → 실패 집계(실패 핸들러, 별도 트랜잭션) 순이라
 * 같은 계정에 동시에 N건을 보내면 모두 "안 잠김"을 읽고 대조까지 간다 — 한도 5회가 톰캣 스레드 수만큼 늘어난다.
 * 여기서는 한 트랜잭션 안에서 계정 행을 FOR UPDATE 로 먼저 잠그고([LoginAttemptService.lockForAttempt]),
 * 대조와 실패 집계를 그 잠금 안에서 끝낸다. 다음 시도는 앞 시도의 집계가 커밋된 뒤에 잠금 판정을 한다.
 *
 * 비밀번호 오류도 트랜잭션은 커밋한다(집계가 남아야 한다). 그 밖의 예외는 기본처럼 그대로 던지고 롤백한다.
 * 없는 계정은 잠글 행이 없어 직렬화되지 않지만, 셀 카운터도 없으므로 지금과 같다.
 */
@Component
class SerializedLoginAuthenticationProvider internal constructor(
    users: UserDetailsService,
    encoder: PasswordEncoder,
    private val attempts: LoginAttemptService,
    private val tx: TransactionOperations,
) : DaoAuthenticationProvider(users) {

    @Autowired
    constructor(users: UserDetailsService, encoder: PasswordEncoder, attempts: LoginAttemptService,
                txManager: PlatformTransactionManager) : this(users, encoder, attempts, TransactionTemplate(txManager))

    init {
        setPasswordEncoder(encoder)
    }

    override fun authenticate(authentication: Authentication): Authentication? {
        val userId = authentication.name
        val outcome = try {
            tx.execute {
                attempts.lockForAttempt(userId)
                try {
                    Outcome(super.authenticate(authentication), null)
                } catch (e: BadCredentialsException) {
                    attempts.onFailure(userId)
                    Outcome(null, e)
                }
            }!!
        } catch (e: AuthenticationException) {
            throw e
        } catch (e: RuntimeException) {
            // 잠금 조회·트랜잭션 오류(USER_ID 중복 등). 기본 provider 가 사용자 조회 오류를 감싸듯 인증 예외로 바꿔
            // 실패 핸들러(/login?error)로 보낸다. 트랜잭션은 이미 되돌렸다.
            throw InternalAuthenticationServiceException(e.message, e)
        }
        outcome.failure?.let { throw it }
        return outcome.result
    }

    private class Outcome(val result: Authentication?, val failure: BadCredentialsException?)
}
