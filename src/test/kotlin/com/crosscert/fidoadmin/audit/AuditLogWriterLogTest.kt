package com.crosscert.fidoadmin.audit

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog
import com.crosscert.fidoadmin.log.repository.CcfaAuditLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

/** 감사 메시지에는 운영자가 입력한 값(여러 줄 설명 등)이 들어간다. 저장 실패 로그는 한 줄이어야 한다. */
class AuditLogWriterLogTest {

    @Test fun saveFailureLogKeepsMessageOnOneLine() {
        val repository: CcfaAuditLogRepository = mock(CcfaAuditLogRepository::class.java)
        val tx: PlatformTransactionManager = mock(PlatformTransactionManager::class.java)
        whenever(tx.getTransaction(anyOrNull())).thenReturn(mock(TransactionStatus::class.java))
        whenever(repository.save(any<CcfaAuditLog>())).thenThrow(IllegalStateException("db down"))
        val logger = LoggerFactory.getLogger(AuditLogWriter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            val writer = AuditLogWriter(repository, tx, 10)
            writer.write(CcfaAuditLog().apply { type = "UPDATE"; message = "T UPDATE 1 | desc: a → b\nERROR fake" })
            writer.shutdown() // 대기열을 비운다

            assertThat(appender.list.map { it.formattedMessage })
                .anySatisfy { assertThat(it).contains("감사 로그 저장 실패").doesNotContain("\n") }
        } finally {
            logger.detachAppender(appender)
        }
    }
}
