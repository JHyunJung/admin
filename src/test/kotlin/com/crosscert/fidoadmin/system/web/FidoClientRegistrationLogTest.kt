package com.crosscert.fidoadmin.system.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient
import com.crosscert.fidoadmin.system.service.FidoClientService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import java.util.Optional

/**
 * 인증 없는 서버 간 경로라 파라미터는 누구나 보낼 수 있다. 방화벽(StrictHttpFirewall)은 경로·헤더의
 * 제어문자는 막지만 파라미터 값은 검사하지 않으므로, fsurl 의 개행이 로그에 그대로 들어가면 가짜 줄이 생긴다.
 */
class FidoClientRegistrationLogTest {

    private val service: FidoClientService = mock()
    private val controller = FidoClientRegistrationController(service)
    private val logger = LoggerFactory.getLogger(FidoClientRegistrationController::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach fun detach() {
        logger.detachAppender(appender)
    }

    private fun lines(): List<String> = appender.list.map { it.formattedMessage }

    @Test fun registrationLogsKeepEachValueOnOneLine() {
        val fsurl = "https://10.0.0.1:8443/fido\n2026-11-12 INFO [ClientAccess] Registered : evil"
        val saved = CcfaFidoclient().apply {
            servercode = "srv\r\nx"
            servername = "srv"
            serverurl = fsurl
            status = "ON"
        }
        whenever(service.register(anyOrNull(), anyOrNull())).thenReturn(saved)

        controller.register("srv\nx", fsurl, MockHttpServletRequest().apply { remoteAddr = "10.0.0.1" })

        assertThat(lines()).isNotEmpty.allSatisfy { assertThat(it).doesNotContain("\n", "\r") }
    }

    @Test fun deregistrationLogKeepsServerNameOnOneLine() {
        whenever(service.deregister(any())).thenReturn(Optional.empty())

        controller.deregister("srv\nfake", MockHttpServletRequest().apply { remoteAddr = "10.0.0.1" })

        assertThat(lines()).isNotEmpty.allSatisfy { assertThat(it).doesNotContain("\n") }
    }
}
