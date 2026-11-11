package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.test.context.ActiveProfiles

/**
 * 같은 계정에 틀린 비밀번호를 동시에 보내도 대조는 한도(PW_FAIL_LIMIT=5)만큼만 일어나고 나머지는 잠금으로 막힌다(진단 78).
 * 앱이 실제로 쓰는 전역 AuthenticationManager 로 확인한다 — 직렬화 provider 가 연결되지 않으면 이 테스트가 깨진다.
 */
@SpringBootTest
@ActiveProfiles("test")
class LoginConcurrencyIntegrationTest : OracleContainerSupport() {

    @Autowired lateinit var authConfig: AuthenticationConfiguration
    @Autowired lateinit var jdbc: JdbcTemplate

    /**
     * 없는 계정은 있는 계정의 비밀번호 오류와 똑같이 BadCredentials 여야 한다. 사용자 조회의 트랜잭션이
     * 로그인 트랜잭션에 참여하므로, 조회 실패가 트랜잭션을 rollback-only 로 만들면 커밋에서 다른 예외가 나
     * 응답이 달라지고 계정 존재 여부가 드러난다.
     */
    @Test
    fun 없는_계정은_비밀번호_오류와_같은_예외다() {
        val manager = authConfig.authenticationManager
        assertThatThrownBy { manager.authenticate(UsernamePasswordAuthenticationToken("it_no_such_user", "whatever")) }
            .isExactlyInstanceOf(BadCredentialsException::class.java)
    }

    @Test
    fun 동시_오입력은_한도만큼만_대조된다() {
        val userId = "it_concurrent_login"
        jdbc.update("DELETE FROM CCFA_MANAGER_PW_POLICY WHERE USER_ID = ?", userId)
        jdbc.update("DELETE FROM CCFA_MANAGER WHERE USER_ID = ?", userId)
        jdbc.update("INSERT INTO CCFA_MANAGER (IDX, USER_ID, USER_PW, USER_NM, COMPANY_IDX, STATUS) " +
            "VALUES (CCFA_MANAGER_SEQ.NEXTVAL, ?, ?, '동시성', 1, '활성')", userId, Sha256PasswordEncoder.sha256Hex("Right1234!"))

        val manager = authConfig.authenticationManager
        val n = 12
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(n)
        try {
            val futures = (1..n).map {
                pool.submit(Callable {
                    start.await()
                    try {
                        manager.authenticate(UsernamePasswordAuthenticationToken(userId, "wrong-$it"))
                        "ok"
                    } catch (e: BadCredentialsException) {
                        "bad"
                    } catch (e: LockedException) {
                        "locked"
                    }
                })
            }
            start.countDown()
            val outcomes = futures.map { it.get(60, TimeUnit.SECONDS) }

            assertThat(outcomes.count { it == "bad" }).isEqualTo(5)
            assertThat(outcomes.count { it == "locked" }).isEqualTo(n - 5)
            val failCnt = jdbc.queryForObject(
                "SELECT MAX(PW_FAIL_CNT) FROM CCFA_MANAGER_PW_POLICY WHERE USER_ID = ?", Long::class.java, userId)
            assertThat(failCnt).isEqualTo(5L)
        } finally {
            pool.shutdownNow()
            jdbc.update("DELETE FROM CCFA_MANAGER_PW_POLICY WHERE USER_ID = ?", userId)
            jdbc.update("DELETE FROM CCFA_MANAGER WHERE USER_ID = ?", userId)
        }
    }
}
