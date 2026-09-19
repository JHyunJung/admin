package com.crosscert.fidoadmin.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import com.crosscert.fidoadmin.system.service.FidoSettingService;
import com.crosscert.fidoadmin.system.web.FidoSettingKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FidoSettingServiceTest {

    CcfaSystemPropRepository repo = mock(CcfaSystemPropRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);
    FidoSettingService service = new FidoSettingService(repo, audit, tenant);

    @BeforeEach void loginSuperSelecting() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        selected.select(9L);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaSystemProp prop(String key, long company, String value) {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId(key, company));
        p.setPropValue(value);
        return p;
    }

    /** 저장된 값이 없으면 enum 의 기본값을 보여준다. 빈칸이면 저장 시 의도치 않은 값이 들어간다. */
    @Test void 값이_없으면_기본값을_돌려준다() {
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());

        Map<String, String> values = service.load();

        assertThat(values.get("CHALLENGE_TERM")).isEqualTo("180");
        assertThat(values.get("SMTP_PORT")).isEqualTo("25");
        assertThat(values).hasSize(FidoSettingKey.values().length);
    }

    /** 저장된 값이 기본값을 덮어쓴다. */
    @Test void 저장된_값이_있으면_그것을_돌려준다() {
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
            .thenReturn(List.of(prop("CHALLENGE_TERM", 9L, "120")));

        assertThat(service.load().get("CHALLENGE_TERM")).isEqualTo("120");
    }

    /**
     * 비밀값은 화면으로 내보내지 않는다.
     *
     * <p>{@code input type=password} 는 눈에만 가려 줄 뿐, th:value 에 들어간 값은 HTML 소스에
     * 그대로 실린다("소스 보기" 로 읽힌다). 그래서 서비스가 값 자체를 마스크로 바꿔 준다.
     */
    @Test void 비밀번호는_실제_값_대신_마스크를_돌려준다() {
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
            .thenReturn(List.of(prop("SMTP_PASSWORD", 9L, "SuperSecret!234")));

        String shown = service.load().get("SMTP_PASSWORD");

        assertThat(shown).isEqualTo(SecretProps.MASK);
        assertThat(shown).doesNotContain("SuperSecret");
    }

    /** 설정되지 않은 비밀번호는 빈 값이다 — 마스크를 주면 "설정되어 있음" 으로 잘못 읽힌다. */
    @Test void 설정되지_않은_비밀번호는_빈_값이다() {
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());

        assertThat(service.load().get("SMTP_PASSWORD")).isEmpty();
    }

    /**
     * 이 테스트가 이번 변경의 핵심이다.
     *
     * <p>화면은 실제 비밀번호를 모른 채 폼을 돌려보낸다. 다른 항목만 바꿔 저장했을 때
     * 그 빈 값(또는 마스크)을 그대로 쓰면 비밀번호가 조용히 지워진다.
     */
    @Test void 다른_설정만_바꿔_저장해도_비밀번호는_유지된다() {
        CcfaSystemProp saved = prop("SMTP_PASSWORD", 9L, "SuperSecret!234");
        when(repo.findById(new CcfaSystemPropId("SMTP_PASSWORD", 9L))).thenReturn(Optional.of(saved));
        when(repo.findById(argThat(id -> !"SMTP_PASSWORD".equals(id.getPropKey()))))
            .thenReturn(Optional.empty());
        when(repo.save(any(CcfaSystemProp.class))).thenAnswer(i -> i.getArgument(0));

        // 화면이 돌려보내는 모습: 비밀번호 칸은 비어 있고, 다른 항목만 바뀌었다.
        service.save(Map.of("CHALLENGE_TERM", "300", "SMTP_PASSWORD", ""));

        assertThat(saved.getPropValue()).isEqualTo("SuperSecret!234");
        // 마스크가 그대로 돌아온 경우도 같다.
        service.save(Map.of("SMTP_PASSWORD", SecretProps.MASK));
        assertThat(saved.getPropValue()).isEqualTo("SuperSecret!234");
    }

    /** 새 값을 입력하면 교체된다 — 위 규칙이 변경 자체를 막아서는 안 된다. */
    @Test void 새_비밀번호를_입력하면_교체된다() {
        CcfaSystemProp saved = prop("SMTP_PASSWORD", 9L, "OldSecret");
        when(repo.findById(new CcfaSystemPropId("SMTP_PASSWORD", 9L))).thenReturn(Optional.of(saved));
        when(repo.save(any(CcfaSystemProp.class))).thenAnswer(i -> i.getArgument(0));

        service.save(Map.of("SMTP_PASSWORD", "NewSecret!999"));

        assertThat(saved.getPropValue()).isEqualTo("NewSecret!999");
    }

    /** 행이 없으면 만든다. 식별자의 COMPANY_IDX 는 유효 테넌트여야 한다. */
    @Test void 행이_없으면_유효_테넌트로_만든다() {
        when(repo.findById(any(CcfaSystemPropId.class))).thenReturn(Optional.empty());
        when(repo.save(any(CcfaSystemProp.class))).thenAnswer(i -> i.getArgument(0));

        service.save(Map.of("SMTP_IP", "10.0.0.1"));

        ArgumentCaptor<CcfaSystemProp> captor = ArgumentCaptor.forClass(CcfaSystemProp.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getId().getPropKey()).isEqualTo("SMTP_IP");
        assertThat(captor.getValue().getId().getCompanyIdx()).isEqualTo(9L);
        assertThat(captor.getValue().getPropValue()).isEqualTo("10.0.0.1");
    }

    /** 행이 있으면 값만 갱신한다. 식별자를 새로 만들면 다른 행이 생긴다. */
    @Test void 행이_있으면_값만_갱신한다() {
        CcfaSystemProp existing = prop("SMTP_IP", 9L, "old");
        when(repo.findById(new CcfaSystemPropId("SMTP_IP", 9L))).thenReturn(Optional.of(existing));
        when(repo.save(any(CcfaSystemProp.class))).thenAnswer(i -> i.getArgument(0));

        service.save(Map.of("SMTP_IP", "10.0.0.2"));

        assertThat(existing.getPropValue()).isEqualTo("10.0.0.2");
        assertThat(existing.getId().getCompanyIdx()).isEqualTo(9L);
    }

    /** 모르는 키는 무시한다. 조작된 POST 로 임의 PROP_KEY 를 쓰지 못하게 한다. */
    @Test void 모르는_키는_저장하지_않는다() {
        service.save(Map.of("NOT_A_SETTING", "x"));
        verify(repo, never()).save(any());
    }

    @Test void 저장하면_감사_로그를_남긴다() {
        when(repo.findById(any(CcfaSystemPropId.class))).thenReturn(Optional.empty());
        when(repo.save(any(CcfaSystemProp.class))).thenAnswer(i -> i.getArgument(0));

        service.save(Map.of("SMTP_IP", "10.0.0.1"));

        verify(audit).log(org.mockito.ArgumentMatchers.eq(AuditType.UPDATE), any(String.class));
    }

    /**
     * 토글은 키마다 다른 문자열로 저장된다. 폼이 보낸 문자열을 그대로 써야 한다 —
     * 서비스가 임의로 정규화하면 FIDO 서버가 읽는 표기와 어긋난다.
     */
    @Test void 토글_값을_받은_표기_그대로_저장한다() {
        when(repo.findById(any(CcfaSystemPropId.class))).thenReturn(Optional.empty());
        when(repo.save(any(CcfaSystemProp.class))).thenAnswer(i -> i.getArgument(0));

        service.save(new java.util.LinkedHashMap<>(Map.of(
            "CERT_P1", "ENABLE",
            "FIDO_ATTESTCERT_AAID_CHECK", "Y",
            "CERT_VERIFY", "no")));

        ArgumentCaptor<CcfaSystemProp> captor = ArgumentCaptor.forClass(CcfaSystemProp.class);
        verify(repo, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(p -> p.getId().getPropKey() + "=" + p.getPropValue())
            .containsExactlyInAnyOrder(
                "CERT_P1=ENABLE", "FIDO_ATTESTCERT_AAID_CHECK=Y", "CERT_VERIFY=no");
    }

    /** 토글의 기본값은 꺼짐이다. 행이 없는 신규 고객사가 설정을 켠 상태로 시작하지 않는다. */
    @Test void 행이_없는_토글은_꺼진_기본값을_돌려준다() {
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(List.of());

        Map<String, String> values = service.load();

        assertThat(values.get("CERT_P1")).isEqualTo("DISABLE");
        assertThat(values.get("FIDO_ATTESTCERT_AAID_CHECK")).isEqualTo("N");
        assertThat(values.get("CERT_VERIFY")).isEqualTo("no");
    }

    /**
     * 오래된 고객사에는 나중에 추가된 키의 행이 아예 없다(CERT_P9, FIDO_DETAIL_LOG_DB_SAVE).
     * 그래도 화면은 열려야 하고, 저장하면 그 고객사에 행이 새로 생겨야 한다.
     */
    @Test void 일부_키만_저장된_고객사도_전체_키를_받는다() {
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
            .thenReturn(List.of(prop("CERT", 9L, "ENABLE")));

        Map<String, String> values = service.load();

        assertThat(values).hasSize(FidoSettingKey.values().length);
        assertThat(values.get("CERT")).isEqualTo("ENABLE");
        assertThat(values.get("CERT_P9")).isEqualTo("DISABLE");
        assertThat(values.get("FIDO_DETAIL_LOG_DB_SAVE")).isEqualTo("DISABLE");
    }
}
