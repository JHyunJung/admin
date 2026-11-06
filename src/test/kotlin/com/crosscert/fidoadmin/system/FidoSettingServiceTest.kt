package com.crosscert.fidoadmin.system

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.reload.FidoConfigChanged
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import com.crosscert.fidoadmin.system.service.FidoSettingService
import com.crosscert.fidoadmin.system.web.FidoSettingKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional
import java.util.function.Function

class FidoSettingServiceTest {

    val repo: CcfaSystemPropRepository = mock()
    val audit: AuditLogger = mock()
    val selected = SelectedTenant()
    val tenant = TenantContext(selected)
    val events: ApplicationEventPublisher = mock()
    val service = FidoSettingService(repo, audit, tenant, events)

    @BeforeEach fun loginSuperSelecting() {
        val u = ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(u, null, u.authorities)
        selected.select(9L)
    }

    @AfterEach fun clear() { SecurityContextHolder.clearContext() }

    private fun prop(key: String, company: Long, value: String?): CcfaSystemProp {
        val p = CcfaSystemProp()
        p.id = CcfaSystemPropId(key, company)
        p.propValue = value
        return p
    }

    /** 저장된 값이 없으면 enum 의 기본값을 보여준다. 빈칸이면 저장 시 의도치 않은 값이 들어간다. */
    @Test fun 값이_없으면_기본값을_돌려준다() {
        whenever(repo.findAll(any<Specification<CcfaSystemProp>>())).thenReturn(listOf())

        val values = service.load()

        assertThat(values["CHALLENGE_TERM"]).isEqualTo("180")
        assertThat(values["SMTP_PORT"]).isEqualTo("25")
        assertThat(values).hasSize(FidoSettingKey.values().size)
    }

    /** 저장된 값이 기본값을 덮어쓴다. */
    @Test fun 저장된_값이_있으면_그것을_돌려준다() {
        whenever(repo.findAll(any<Specification<CcfaSystemProp>>()))
            .thenReturn(listOf(prop("CHALLENGE_TERM", 9L, "120")))

        assertThat(service.load()["CHALLENGE_TERM"]).isEqualTo("120")
    }

    /**
     * 비밀값은 화면으로 내보내지 않는다.
     *
     * `input type=password` 는 눈에만 가려 줄 뿐, th:value 에 들어간 값은 HTML 소스에
     * 그대로 실린다("소스 보기" 로 읽힌다). 그래서 서비스가 값 자체를 마스크로 바꿔 준다.
     */
    @Test fun 비밀번호는_실제_값_대신_마스크를_돌려준다() {
        whenever(repo.findAll(any<Specification<CcfaSystemProp>>()))
            .thenReturn(listOf(prop("SMTP_PASSWORD", 9L, "SuperSecret!234")))

        val shown = service.load()["SMTP_PASSWORD"]

        assertThat(shown).isEqualTo(SecretProps.MASK)
        assertThat(shown).doesNotContain("SuperSecret")
    }

    /** 설정되지 않은 비밀번호는 빈 값이다 — 마스크를 주면 "설정되어 있음" 으로 잘못 읽힌다. */
    @Test fun 설정되지_않은_비밀번호는_빈_값이다() {
        whenever(repo.findAll(any<Specification<CcfaSystemProp>>())).thenReturn(listOf())

        assertThat(service.load()["SMTP_PASSWORD"]).isEmpty()
    }

    /**
     * 이 테스트가 이번 변경의 핵심이다.
     *
     * 화면은 실제 비밀번호를 모른 채 폼을 돌려보낸다. 다른 항목만 바꿔 저장했을 때
     * 그 빈 값(또는 마스크)을 그대로 쓰면 비밀번호가 조용히 지워진다.
     */
    @Test fun 다른_설정만_바꿔_저장해도_비밀번호는_유지된다() {
        val saved = prop("SMTP_PASSWORD", 9L, "SuperSecret!234")
        whenever(repo.findById(CcfaSystemPropId("SMTP_PASSWORD", 9L))).thenReturn(Optional.of(saved))
        whenever(repo.findById(argThat<CcfaSystemPropId> { "SMTP_PASSWORD" != propKey }))
            .thenReturn(Optional.empty())
        whenever(repo.save(any<CcfaSystemProp>())).thenAnswer { it.getArgument<CcfaSystemProp>(0) }

        // 화면이 돌려보내는 모습: 비밀번호 칸은 비어 있고, 다른 항목만 바뀌었다.
        service.save(mapOf("CHALLENGE_TERM" to "300", "SMTP_PASSWORD" to ""))

        assertThat(saved.propValue).isEqualTo("SuperSecret!234")
        // 마스크가 그대로 돌아온 경우도 같다.
        service.save(mapOf("SMTP_PASSWORD" to SecretProps.MASK))
        assertThat(saved.propValue).isEqualTo("SuperSecret!234")
    }

    /** 새 값을 입력하면 교체된다 — 위 규칙이 변경 자체를 막아서는 안 된다. */
    @Test fun 새_비밀번호를_입력하면_교체된다() {
        val saved = prop("SMTP_PASSWORD", 9L, "OldSecret")
        whenever(repo.findById(CcfaSystemPropId("SMTP_PASSWORD", 9L))).thenReturn(Optional.of(saved))
        whenever(repo.save(any<CcfaSystemProp>())).thenAnswer { it.getArgument<CcfaSystemProp>(0) }

        service.save(mapOf("SMTP_PASSWORD" to "NewSecret!999"))

        assertThat(saved.propValue).isEqualTo("NewSecret!999")
    }

    /** 행이 없으면 만든다. 식별자의 COMPANY_IDX 는 유효 테넌트여야 한다. */
    @Test fun 행이_없으면_유효_테넌트로_만든다() {
        whenever(repo.findById(any<CcfaSystemPropId>())).thenReturn(Optional.empty())
        whenever(repo.save(any<CcfaSystemProp>())).thenAnswer { it.getArgument<CcfaSystemProp>(0) }

        service.save(mapOf("SMTP_IP" to "10.0.0.1"))

        val captor = argumentCaptor<CcfaSystemProp>()
        verify(repo).save(captor.capture())
        assertThat(captor.firstValue.id!!.propKey).isEqualTo("SMTP_IP")
        assertThat(captor.firstValue.id!!.companyIdx).isEqualTo(9L)
        assertThat(captor.firstValue.propValue).isEqualTo("10.0.0.1")
    }

    /** 행이 있으면 값만 갱신한다. 식별자를 새로 만들면 다른 행이 생긴다. */
    @Test fun 행이_있으면_값만_갱신한다() {
        val existing = prop("SMTP_IP", 9L, "old")
        whenever(repo.findById(CcfaSystemPropId("SMTP_IP", 9L))).thenReturn(Optional.of(existing))
        whenever(repo.save(any<CcfaSystemProp>())).thenAnswer { it.getArgument<CcfaSystemProp>(0) }

        service.save(mapOf("SMTP_IP" to "10.0.0.2"))

        assertThat(existing.propValue).isEqualTo("10.0.0.2")
        assertThat(existing.id!!.companyIdx).isEqualTo(9L)
    }

    /** 모르는 키는 무시한다. 조작된 POST 로 임의 PROP_KEY 를 쓰지 못하게 한다. */
    @Test fun 모르는_키는_저장하지_않는다() {
        service.save(mapOf("NOT_A_SETTING" to "x"))
        verify(repo, never()).save(any<CcfaSystemProp>())
    }

    @Test fun 저장하면_감사_로그를_남긴다() {
        whenever(repo.findById(any<CcfaSystemPropId>())).thenReturn(Optional.empty())
        whenever(repo.save(any<CcfaSystemProp>())).thenAnswer { it.getArgument<CcfaSystemProp>(0) }

        service.save(mapOf("SMTP_IP" to "10.0.0.1"))

        verify(audit).log(eq(AuditType.UPDATE), any<String>())
    }

    /**
     * 토글은 키마다 다른 문자열로 저장된다. 폼이 보낸 문자열을 그대로 써야 한다 —
     * 서비스가 임의로 정규화하면 FIDO 서버가 읽는 표기와 어긋난다.
     */
    @Test fun 토글_값을_받은_표기_그대로_저장한다() {
        whenever(repo.findById(any<CcfaSystemPropId>())).thenReturn(Optional.empty())
        whenever(repo.save(any<CcfaSystemProp>())).thenAnswer { it.getArgument<CcfaSystemProp>(0) }

        service.save(LinkedHashMap(mapOf(
            "CERT_P1" to "ENABLE",
            "FIDO_ATTESTCERT_AAID_CHECK" to "Y",
            "CERT_VERIFY" to "no")))

        val captor = argumentCaptor<CcfaSystemProp>()
        verify(repo, times(3)).save(captor.capture())
        assertThat(captor.allValues)
            .extracting(Function<CcfaSystemProp, String> { p -> p.id!!.propKey + "=" + p.propValue })
            .containsExactlyInAnyOrder(
                "CERT_P1=ENABLE", "FIDO_ATTESTCERT_AAID_CHECK=Y", "CERT_VERIFY=no")
    }

    /** 토글의 기본값은 꺼짐이다. 행이 없는 신규 고객사가 설정을 켠 상태로 시작하지 않는다. */
    @Test fun 행이_없는_토글은_꺼진_기본값을_돌려준다() {
        whenever(repo.findAll(any<Specification<CcfaSystemProp>>())).thenReturn(listOf())

        val values = service.load()

        assertThat(values["CERT_P1"]).isEqualTo("DISABLE")
        assertThat(values["FIDO_ATTESTCERT_AAID_CHECK"]).isEqualTo("N")
        assertThat(values["CERT_VERIFY"]).isEqualTo("no")
    }

    /**
     * 오래된 고객사에는 나중에 추가된 키의 행이 아예 없다(CERT_P9, FIDO_DETAIL_LOG_DB_SAVE).
     * 그래도 화면은 열려야 하고, 저장하면 그 고객사에 행이 새로 생겨야 한다.
     */
    @Test fun 일부_키만_저장된_고객사도_전체_키를_받는다() {
        whenever(repo.findAll(any<Specification<CcfaSystemProp>>()))
            .thenReturn(listOf(prop("CERT", 9L, "ENABLE")))

        val values = service.load()

        assertThat(values).hasSize(FidoSettingKey.values().size)
        assertThat(values["CERT"]).isEqualTo("ENABLE")
        assertThat(values["CERT_P9"]).isEqualTo("DISABLE")
        assertThat(values["FIDO_DETAIL_LOG_DB_SAVE"]).isEqualTo("DISABLE")
    }

    @Test fun saveWithChangesPublishesOnce() {
        whenever(repo.findById(any<CcfaSystemPropId>())).thenReturn(Optional.empty())
        whenever(repo.save(any<CcfaSystemProp>())).thenAnswer { it.getArgument<CcfaSystemProp>(0) }

        service.save(LinkedHashMap(mapOf("SMTP_IP" to "10.0.0.1", "CHALLENGE_TERM" to "300")))

        verify(events, times(1)).publishEvent(FidoConfigChanged("FIDO 서버 설정 저장 2건") as Any)
    }

    @Test fun saveWithoutChangesDoesNotPublish() {
        whenever(repo.findById(CcfaSystemPropId("SMTP_IP", 9L))).thenReturn(Optional.of(prop("SMTP_IP", 9L, "10.0.0.1")))

        service.save(mapOf("SMTP_IP" to "10.0.0.1"))

        verify(events, never()).publishEvent(any<Any>())
    }
}
