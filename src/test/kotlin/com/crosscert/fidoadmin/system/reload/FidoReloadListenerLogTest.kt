package com.crosscert.fidoadmin.system.reload

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.util.concurrent.Executor

/**
 * 서버 코드·URL 은 인증 없는 자가등록(/api/svc/reg)으로 DB 에 들어온 외부 값이다. reload 결과 로그에 다시 찍힐 때
 * CR/LF 뿐 아니라 NEL·U+2028 같은 줄 구분 문자도 한 줄로 만든다.
 */
class FidoReloadListenerLogTest {

    @Test fun reloadResultLogsKeepExternalValuesOnOneLine() {
        val client: FidoReloadClient = mock()
        val url = "http://x\u0085INFO fake y"
        whenever(client.reloadAll()).thenReturn(listOf(
            ReloadResult("ok srv", url, true, "HTTP 200", 1),
            ReloadResult("bad\u0085srv", url, false, "지원하지 않는 URL: $url", 0),
        ))
        val listener = FidoReloadListener(client, Executor { it.run() })
        val logger = LoggerFactory.getLogger(FidoReloadListener::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            listener.on(FidoConfigChanged("AAID 등록 a b"))

            assertThat(appender.list.map { it.formattedMessage }).hasSize(2)
                .allSatisfy { assertThat(it).doesNotContain("\u0085", " ", " ", "\n") }
        } finally {
            logger.detachAppender(appender)
        }
    }
}
