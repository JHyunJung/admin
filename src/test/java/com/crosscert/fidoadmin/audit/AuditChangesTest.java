package com.crosscert.fidoadmin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditChangesTest {

    /** 엔티티를 흉내 낸 단순 객체. 컬렉션 필드는 비교에서 빠져야 한다. */
    static class Row {
        String userNm;
        String userPw;
        Long count;
        LocalDateTime updatedtime;
        List<String> tags = List.of("a");
    }

    AuditLogger audit = mock(AuditLogger.class);

    @Test void snapshotSkipsCollectionsAndKeepsSimpleFields() {
        Row r = new Row();
        r.userNm = "홍길동"; r.count = 3L;

        Map<String, String> snap = AuditChanges.snapshot(r);

        assertThat(snap).containsKeys("userNm", "userPw", "count", "updatedtime");
        assertThat(snap).doesNotContainKey("tags");
        assertThat(snap.get("userNm")).isEqualTo("홍길동");
        assertThat(snap.get("userPw")).isNull();
    }

    @Test void describeListsOnlyChangedFields() {
        Row before = new Row(); before.userNm = "홍길동"; before.count = 3L;
        Row after = new Row(); after.userNm = "김철수"; after.count = 3L;

        String diff = AuditChanges.describe(AuditChanges.snapshot(before), AuditChanges.snapshot(after));

        assertThat(diff).isEqualTo("userNm: 홍길동 → 김철수");
    }

    /** Oracle 은 빈 문자열을 NULL 로 저장한다. 폼의 "" 와 DB 의 null 을 같은 값으로 봐야 한다. */
    @Test void blankAndNullAreTheSameValue() {
        Row before = new Row(); before.userNm = null;
        Row after = new Row(); after.userNm = "";

        assertThat(AuditChanges.describe(AuditChanges.snapshot(before), AuditChanges.snapshot(after))).isEmpty();
    }

    /**
     * 비밀 필드는 값 대신 마스크를 남긴다. 필드명이 낙타 표기(userPw)라 컬럼명 규칙(_PW)에
     * 그대로는 걸리지 않는다 — 정규화 없이 넘기면 비밀번호 해시가 감사 로그에 실린다.
     */
    @Test void secretFieldsAreMaskedEvenInCamelCase() {
        Row before = new Row(); before.userPw = "old-hash";
        Row after = new Row(); after.userPw = "new-hash";

        String diff = AuditChanges.describe(AuditChanges.snapshot(before), AuditChanges.snapshot(after));

        assertThat(diff).isEqualTo("userPw: ******** → ********");
        assertThat(diff).doesNotContain("old-hash", "new-hash");
    }

    @Test void columnNameConvertsCamelCase() {
        assertThat(AuditChanges.columnName("userPw")).isEqualTo("USER_PW");
        assertThat(AuditChanges.columnName("smtpPassword")).isEqualTo("SMTP_PASSWORD");
        assertThat(AuditChanges.columnName("idx")).isEqualTo("IDX");
    }

    @Test void nullBecomesPlaceholder() {
        Row before = new Row();
        Row after = new Row(); after.userNm = "홍길동";

        assertThat(AuditChanges.describe(AuditChanges.snapshot(before), AuditChanges.snapshot(after)))
            .isEqualTo("userNm: (없음) → 홍길동");
    }

    /** 바뀐 것이 없으면 기록하지 않는다. 저장 버튼만 누른 요청이 실제 변경을 묻어서는 안 된다. */
    @Test void recordSkipsWhenNothingChanged() {
        Row r = new Row(); r.userNm = "홍길동";
        Map<String, String> before = AuditChanges.snapshot(r);

        AuditChanges.record(audit, "CCFA_MANAGER", "2", before, r);

        verify(audit, never()).log(eq(AuditType.UPDATE), anyString());
    }

    @Test void recordWritesTableIdAndDiff() {
        Row r = new Row(); r.userNm = "홍길동";
        Map<String, String> before = AuditChanges.snapshot(r);
        r.userNm = "김철수";

        AuditChanges.record(audit, "CCFA_MANAGER", "2", before, r);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq(AuditType.UPDATE), msg.capture());
        assertThat(msg.getValue()).isEqualTo("CCFA_MANAGER UPDATE 2 | userNm: 홍길동 → 김철수");
    }
}
