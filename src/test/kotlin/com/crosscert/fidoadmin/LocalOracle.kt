package com.crosscert.fidoadmin

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * `local` 프로파일(로컬 도커 Oracle, localhost:1521)을 요구하는 테스트의 실행 조건.
 *
 * 그 DB 가 없으면 컨텍스트 로딩이 실패해 "빨간" 결과가 나는데, 그것은 코드 결함이 아니라 환경 부재다.
 * `@EnabledIf("com.crosscert.fidoadmin.LocalOracle#isUp")` 로 걸어 두면 DB 가 없는 환경(CI 등)에서는
 * 건너뛰고, 있는 환경에서는 지금처럼 돈다. Testcontainers 테스트가 `disabledWithoutDocker` 로
 * 하는 것과 같은 판단이다.
 */
object LocalOracle {

    @JvmStatic
    fun isUp(): Boolean {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("localhost", 1521), 500)
                return true
            }
        } catch (e: IOException) {
            return false
        }
    }
}
