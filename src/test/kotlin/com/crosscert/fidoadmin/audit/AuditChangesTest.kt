package com.crosscert.fidoadmin.audit

import org.mockito.kotlin.argumentCaptor
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.eq

class AuditChangesTest {

    /** 엔티티를 흉내 낸 단순 객체. 컬렉션 필드는 비교에서 빠져야 한다. */
    class Row {
        var userNm: String? = null
        var userPw: String? = null
        var count: Long? = null
        var updatedtime: LocalDateTime? = null
        var tags: List<String> = listOf("a")
    }

    val audit: AuditLogger = mock(AuditLogger::class.java)

    @Test fun snapshotSkipsCollectionsAndKeepsSimpleFields() {
        val r = Row()
        r.userNm = "홍길동"; r.count = 3L

        val snap = AuditChanges.snapshot(r)

        assertThat(snap).containsKeys("userNm", "userPw", "count", "updatedtime")
        assertThat(snap).doesNotContainKey("tags")
        assertThat(snap["userNm"]).isEqualTo("홍길동")
        assertThat(snap["userPw"]).isNull()
    }

    @Test fun describeListsOnlyChangedFields() {
        val before = Row(); before.userNm = "홍길동"; before.count = 3L
        val after = Row(); after.userNm = "김철수"; after.count = 3L

        val diff = AuditChanges.describe(AuditChanges.snapshot(before), AuditChanges.snapshot(after))

        assertThat(diff).isEqualTo("userNm: 홍길동 → 김철수")
    }

    /** Oracle 은 빈 문자열을 NULL 로 저장한다. 폼의 "" 와 DB 의 null 을 같은 값으로 봐야 한다. */
    @Test fun blankAndNullAreTheSameValue() {
        val before = Row(); before.userNm = null
        val after = Row(); after.userNm = ""

        assertThat(AuditChanges.describe(AuditChanges.snapshot(before), AuditChanges.snapshot(after))).isEmpty()
    }

    /**
     * 비밀 필드는 값 대신 마스크를 남긴다. 필드명이 낙타 표기(userPw)라 컬럼명 규칙(_PW)에
     * 그대로는 걸리지 않는다 — 정규화 없이 넘기면 비밀번호 해시가 감사 로그에 실린다.
     */
    @Test fun secretFieldsAreMaskedEvenInCamelCase() {
        val before = Row(); before.userPw = "old-hash"
        val after = Row(); after.userPw = "new-hash"

        val diff = AuditChanges.describe(AuditChanges.snapshot(before), AuditChanges.snapshot(after))

        assertThat(diff).isEqualTo("userPw: ******** → ********")
        assertThat(diff).doesNotContain("old-hash", "new-hash")
    }

    @Test fun columnNameConvertsCamelCase() {
        assertThat(AuditChanges.columnName("userPw")).isEqualTo("USER_PW")
        assertThat(AuditChanges.columnName("smtpPassword")).isEqualTo("SMTP_PASSWORD")
        assertThat(AuditChanges.columnName("idx")).isEqualTo("IDX")
    }

    @Test fun nullBecomesPlaceholder() {
        val before = Row()
        val after = Row(); after.userNm = "홍길동"

        assertThat(AuditChanges.describe(AuditChanges.snapshot(before), AuditChanges.snapshot(after)))
            .isEqualTo("userNm: (없음) → 홍길동")
    }

    /** 바뀐 것이 없으면 기록하지 않는다. 저장 버튼만 누른 요청이 실제 변경을 묻어서는 안 된다. */
    @Test fun recordSkipsWhenNothingChanged() {
        val r = Row(); r.userNm = "홍길동"
        val before = AuditChanges.snapshot(r)

        AuditChanges.record(audit, "CCFA_MANAGER", "2", before, r)

        verify(audit, never()).log(eq(AuditType.UPDATE), anyString())
    }

    /**
     * CCFA_SYSTEM_PROP 은 범용 key-value 라 비밀 여부가 필드명(propValue)이 아니라
     * 키(SMTP_PASSWORD)에 있다. 필드명만 보면 SMTP 비밀번호 전후 값이 감사 로그에 평문으로 실리고,
     * 그 테넌트의 COMPANY 운영자가 /logs/audit 에서 읽는다.
     */
    @Test fun systemPropWithSecretKeyMasksItsValue() {
        val row = CcfaSystemProp()
        row.id = CcfaSystemPropId("SMTP_PASSWORD", 5L)
        row.propValue = "old-secret"
        val before = AuditChanges.snapshot(row)
        row.propValue = "new-secret"

        AuditChanges.record(audit, "CCFA_SYSTEM_PROP", "SMTP_PASSWORD@5", before, row)

        val msg = argumentCaptor<String>()
        verify(audit).log(eq(AuditType.UPDATE), msg.capture())
        assertThat(msg.lastValue).contains("propValue: ******** → ********")
        assertThat(msg.lastValue).doesNotContain("old-secret", "new-secret")
    }

    /** 비밀 키가 아닌 시스템 설정은 지금처럼 값이 그대로 남아야 한다(변경 추적이 목적이다). */
    @Test fun systemPropWithPlainKeyKeepsItsValue() {
        val row = CcfaSystemProp()
        row.id = CcfaSystemPropId("SMTP_HOST", 5L)
        row.propValue = "mail.old"
        val before = AuditChanges.snapshot(row)
        row.propValue = "mail.new"

        AuditChanges.record(audit, "CCFA_SYSTEM_PROP", "SMTP_HOST@5", before, row)

        val msg = argumentCaptor<String>()
        verify(audit).log(eq(AuditType.UPDATE), msg.capture())
        assertThat(msg.lastValue).contains("propValue: mail.old → mail.new")
    }

    @Test fun recordWritesTableIdAndDiff() {
        val r = Row(); r.userNm = "홍길동"
        val before = AuditChanges.snapshot(r)
        r.userNm = "김철수"

        AuditChanges.record(audit, "CCFA_MANAGER", "2", before, r)

        val msg = argumentCaptor<String>()
        verify(audit).log(eq(AuditType.UPDATE), msg.capture())
        assertThat(msg.lastValue).isEqualTo("CCFA_MANAGER UPDATE 2 | userNm: 홍길동 → 김철수")
    }
}
