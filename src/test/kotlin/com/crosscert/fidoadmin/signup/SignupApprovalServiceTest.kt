package com.crosscert.fidoadmin.signup

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.PasswordExpiryPolicy
import com.crosscert.fidoadmin.common.ManagerStatus
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import com.crosscert.fidoadmin.manager.service.ManagerUserIdGuard
import jakarta.persistence.EntityManager
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class SignupApprovalServiceTest {

    val managers: CcfaManagerRepository = mock(CcfaManagerRepository::class.java)
    val em: EntityManager = mock(EntityManager::class.java)
    val audit: AuditLogger = mock(AuditLogger::class.java)
    val expiry: PasswordExpiryPolicy = mock(PasswordExpiryPolicy::class.java)
    val companies: CcfaCompanyRepository = mock(CcfaCompanyRepository::class.java)
    val service = SignupService(managers, companies, ManagerUserIdGuard(managers, em), audit, expiry)

    private fun pending(): CcfaManager {
        val m = CcfaManager()
        m.idx = 5L
        m.userId = "newbie"
        m.userNm = "홍길동"
        m.status = "승인대기"
        m.companyIdx = -1L
        m.etc = "신청 사유: 업무 담당자입니다"
        m.createdtime = LocalDateTime.now()
        return m
    }

    @BeforeEach fun stub() {
        whenever(managers.save(any<CcfaManager>())).thenAnswer { it.getArgument<CcfaManager>(0) }
        // 기본값: 넘어오는 고객사는 실재한다. 없는 경우만 테스트에서 따로 지정한다.
        whenever(companies.existsById(any<Long>())).thenReturn(true)
    }

    @Test fun pendingListsOldestFirst() {
        val a = pending()
        whenever(managers.findByStatusOrderByCreatedtimeAsc("승인대기")).thenReturn(listOf(a))

        assertThat(service.pending()).containsExactly(a)
    }

    @Test fun approveAssignsCompanyAndActivates() {
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()))

        val saved = service.approve(5L, 1L)

        assertThat(saved.status).isEqualTo(ManagerStatus.ACTIVE)
        assertThat(saved.companyIdx).isEqualTo(1L)
        assertThat(saved.updatedtime).isNotNull()
        verify(expiry).touch("newbie")
    }

    /** 권한 상승 차단 2단계: 승인으로 SUPER 를 만들 수 없다. */
    @Test fun approveRejectsSuperCompanyIdx() {
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()))

        assertThatThrownBy { service.approve(5L, 0L) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("전역")
        verify(managers, never()).save(any<CcfaManager>())
    }

    /** 미배정 표식으로는 승인할 수 없다. */
    @Test fun approveRejectsUnassignedCompanyIdx() {
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()))

        assertThatThrownBy { service.approve(5L, -1L) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(managers, never()).save(any<CcfaManager>())
    }

    /** 소속을 고르지 않은 채 넘어온 null 도 거부한다. */
    @Test fun approveRejectsNullCompanyIdx() {
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()))

        assertThatThrownBy { service.approve(5L, null) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(managers, never()).save(any<CcfaManager>())
    }

    /**
     * 실재하지 않는 고객사로는 승인할 수 없다. CCFA_MANAGER 에 CCFA_COMPANY 로 가는
     * 외래키가 없어 DB 가 걸러 주지 않으므로 서비스가 직접 확인해야 한다.
     * 검증은 행을 잠그기 전에 끝나야 하니 잠금 조회 자체가 일어나지 않는다.
     */
    @Test fun approveRejectsCompanyThatDoesNotExist() {
        whenever(companies.existsById(99999L)).thenReturn(false)

        assertThatThrownBy { service.approve(5L, 99999L) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("존재하지 않는 고객사")
        verify(managers, never()).findByIdxForUpdate(anyOrNull())
        verify(managers, never()).save(any<CcfaManager>())
    }

    /** 권한 상승 차단 3단계: 이미 활성인 계정은 이 경로로 못 바꾼다. */
    @Test fun approveRejectsAlreadyActiveAccount() {
        val active = pending()
        active.status = ManagerStatus.ACTIVE
        active.companyIdx = 1L
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(active))

        assertThatThrownBy { service.approve(5L, 2L) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("승인대기")
        verify(managers, never()).save(any<CcfaManager>())
    }

    @Test fun approveFailsWhenTargetMissing() {
        whenever(managers.findByIdxForUpdate(9L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.approve(9L, 1L) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    /**
     * 상태 재확인은 행을 잠근 뒤에 해야 한다. 잠그기 전에 읽으면 동시에 들어온 두 승인이
     * 둘 다 "승인대기"를 보고 통과한다. 잠금 조회 결과로만 상태를 판정하는지 고정한다.
     */
    @Test fun approveRechecksStatusAfterRowLock() {
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()))

        service.approve(5L, 1L)

        val order = Mockito.inOrder(managers)
        order.verify(managers).findByIdxForUpdate(5L)
        order.verify(managers).save(any<CcfaManager>())
        // 잠금 없는 조회로 상태를 판정하지 않는다
        verify(managers, never()).findById(anyOrNull())
    }

    /** 승인·거절은 로그인한 슈퍼 관리자가 수행하므로 감사 로그가 남는다. */
    @Test fun approveWritesAuditLog() {
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()))

        service.approve(5L, 7L)

        verify(audit).logGlobal(eq(AuditType.STATUS),
            contains("SIGNUP APPROVE newbie"))
    }

    @Test fun rejectSetsRejectedStatusAndKeepsReason() {
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()))

        val saved = service.reject(5L, "소속 확인 불가")

        assertThat(saved.status).isEqualTo("거절")
        assertThat(saved.etc).contains("업무 담당자입니다")
        assertThat(saved.etc).contains("거절 사유: 소속 확인 불가")
        // 거절은 소속을 배정하지 않는다
        assertThat(saved.companyIdx).isEqualTo(-1L)
    }

    @Test fun rejectRejectsAlreadyActiveAccount() {
        val active = pending()
        active.status = ManagerStatus.ACTIVE
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(active))

        assertThatThrownBy { service.reject(5L, "사유") }
            .isInstanceOf(IllegalStateException::class.java)
        verify(managers, never()).save(any<CcfaManager>())
    }

    @Test fun rejectWritesAuditLog() {
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()))

        service.reject(5L, "사유")

        verify(audit).logGlobal(eq(AuditType.STATUS), contains("SIGNUP REJECT newbie"))
    }

    @Test fun rejectKeepsReasonPlaceholderWhenBlank() {
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()))

        val saved = service.reject(5L, "   ")

        assertThat(saved.etc).contains("거절 사유: (사유 없음)")
    }

    /**
     * ETC 는 2048 **바이트** 제한이다(문자 수가 아니다. 한글 1자 = 3바이트).
     * 문자 수로 자르면 한글 입력에서 ORA-12899 로 저장이 실패한다.
     * 잘린 끝에서 글자가 쪼개져 U+FFFD 가 들어가서도 안 된다.
     */
    @Test fun rejectTruncatesOverlongEtcByBytesWithoutBreakingCharacters() {
        val m = pending()
        // 신청 사유 최대치(1700바이트) + 접두사 근처까지 채운다
        m.etc = "신청 사유: " + "가".repeat(560)
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(m))

        val saved = service.reject(5L, "나".repeat(300))

        val stored = saved.etc!!.toByteArray(StandardCharsets.UTF_8)
        assertThat(stored.size).isLessThanOrEqualTo(2048)
        assertThat(saved.etc).doesNotContain("�")
        assertThat(saved.etc).contains("거절 사유: 나")
    }

    /** 자르기가 필요 없을 때는 원문 그대로 둔다. */
    @Test fun rejectKeepsShortEtcIntact() {
        whenever(managers.findByIdxForUpdate(5L)).thenReturn(Optional.of(pending()))

        val saved = service.reject(5L, "짧은 사유")

        assertThat(saved.etc)
            .isEqualTo("신청 사유: 업무 담당자입니다\n거절 사유: 짧은 사유")
    }
}
