package com.crosscert.fidoadmin.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.ManagerStatus;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class SignupApprovalServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    EntityManager em = mock(EntityManager.class);
    AuditLogger audit = mock(AuditLogger.class);
    SignupService service = new SignupService(managers, em, audit);

    private CcfaManager pending() {
        CcfaManager m = new CcfaManager();
        m.setIdx(5L);
        m.setUserId("newbie");
        m.setUserNm("홍길동");
        m.setStatus("승인대기");
        m.setCompanyIdx(-1L);
        m.setEtc("신청 사유: 업무 담당자입니다");
        m.setCreatedtime(LocalDateTime.now());
        return m;
    }

    @BeforeEach void stub() {
        when(managers.save(any(CcfaManager.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test void pendingListsOldestFirst() {
        CcfaManager a = pending();
        when(managers.findByStatusOrderByCreatedtimeAsc("승인대기")).thenReturn(List.of(a));

        assertThat(service.pending()).containsExactly(a);
    }

    @Test void approveAssignsCompanyAndActivates() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        CcfaManager saved = service.approve(5L, 1L);

        assertThat(saved.getStatus()).isEqualTo(ManagerStatus.ACTIVE);
        assertThat(saved.getCompanyIdx()).isEqualTo(1L);
        assertThat(saved.getUpdatedtime()).isNotNull();
    }

    /** 권한 상승 차단 2단계: 승인으로 SUPER 를 만들 수 없다. */
    @Test void approveRejectsSuperCompanyIdx() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.approve(5L, 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("전역");
        verify(managers, never()).save(any(CcfaManager.class));
    }

    /** 미배정 표식으로는 승인할 수 없다. */
    @Test void approveRejectsUnassignedCompanyIdx() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.approve(5L, -1L))
            .isInstanceOf(IllegalArgumentException.class);
        verify(managers, never()).save(any(CcfaManager.class));
    }

    /** 소속을 고르지 않은 채 넘어온 null 도 거부한다. */
    @Test void approveRejectsNullCompanyIdx() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        assertThatThrownBy(() -> service.approve(5L, null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(managers, never()).save(any(CcfaManager.class));
    }

    /** 권한 상승 차단 3단계: 이미 활성인 계정은 이 경로로 못 바꾼다. */
    @Test void approveRejectsAlreadyActiveAccount() {
        CcfaManager active = pending();
        active.setStatus(ManagerStatus.ACTIVE);
        active.setCompanyIdx(1L);
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.approve(5L, 2L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("승인대기");
        verify(managers, never()).save(any(CcfaManager.class));
    }

    @Test void approveFailsWhenTargetMissing() {
        when(managers.findByIdxForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(9L, 1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 상태 재확인은 행을 잠근 뒤에 해야 한다. 잠그기 전에 읽으면 동시에 들어온 두 승인이
     * 둘 다 "승인대기"를 보고 통과한다. 잠금 조회 결과로만 상태를 판정하는지 고정한다.
     */
    @Test void approveRechecksStatusAfterRowLock() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        service.approve(5L, 1L);

        InOrder order = Mockito.inOrder(managers);
        order.verify(managers).findByIdxForUpdate(5L);
        order.verify(managers).save(any(CcfaManager.class));
        // 잠금 없는 조회로 상태를 판정하지 않는다
        verify(managers, never()).findById(any());
    }

    /** 승인·거절은 로그인한 슈퍼 관리자가 수행하므로 감사 로그가 남는다. */
    @Test void approveWritesAuditLog() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        service.approve(5L, 7L);

        verify(audit).log(eq(AuditType.STATUS),
            Mockito.contains("SIGNUP APPROVE newbie"));
    }

    @Test void rejectSetsRejectedStatusAndKeepsReason() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        CcfaManager saved = service.reject(5L, "소속 확인 불가");

        assertThat(saved.getStatus()).isEqualTo("거절");
        assertThat(saved.getEtc()).contains("업무 담당자입니다");
        assertThat(saved.getEtc()).contains("거절 사유: 소속 확인 불가");
        // 거절은 소속을 배정하지 않는다
        assertThat(saved.getCompanyIdx()).isEqualTo(-1L);
    }

    @Test void rejectRejectsAlreadyActiveAccount() {
        CcfaManager active = pending();
        active.setStatus(ManagerStatus.ACTIVE);
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.reject(5L, "사유"))
            .isInstanceOf(IllegalStateException.class);
        verify(managers, never()).save(any(CcfaManager.class));
    }

    @Test void rejectWritesAuditLog() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        service.reject(5L, "사유");

        verify(audit).log(eq(AuditType.STATUS), Mockito.contains("SIGNUP REJECT newbie"));
    }

    @Test void rejectKeepsReasonPlaceholderWhenBlank() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        CcfaManager saved = service.reject(5L, "   ");

        assertThat(saved.getEtc()).contains("거절 사유: (사유 없음)");
    }

    /**
     * ETC 는 2048 <b>바이트</b> 제한이다(문자 수가 아니다. 한글 1자 = 3바이트).
     * 문자 수로 자르면 한글 입력에서 ORA-12899 로 저장이 실패한다.
     * 잘린 끝에서 글자가 쪼개져 U+FFFD 가 들어가서도 안 된다.
     */
    @Test void rejectTruncatesOverlongEtcByBytesWithoutBreakingCharacters() {
        CcfaManager m = pending();
        // 신청 사유 최대치(1700바이트) + 접두사 근처까지 채운다
        m.setEtc("신청 사유: " + "가".repeat(560));
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(m));

        CcfaManager saved = service.reject(5L, "나".repeat(300));

        byte[] stored = saved.getEtc().getBytes(StandardCharsets.UTF_8);
        assertThat(stored.length).isLessThanOrEqualTo(2048);
        assertThat(saved.getEtc()).doesNotContain("�");
        assertThat(saved.getEtc()).contains("거절 사유: 나");
    }

    /** 자르기가 필요 없을 때는 원문 그대로 둔다. */
    @Test void rejectKeepsShortEtcIntact() {
        when(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()));

        CcfaManager saved = service.reject(5L, "짧은 사유");

        assertThat(saved.getEtc())
            .isEqualTo("신청 사유: 업무 담당자입니다\n거절 사유: 짧은 사유");
    }
}
