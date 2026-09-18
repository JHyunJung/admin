package com.crosscert.fidoadmin.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        assertThat(values.get("CHALLENGE_EXPIRE_SECONDS")).isEqualTo("60");
        assertThat(values.get("SMTP_PORT")).isEqualTo("25");
        assertThat(values).hasSize(FidoSettingKey.values().length);
    }

    /** 저장된 값이 기본값을 덮어쓴다. */
    @Test void 저장된_값이_있으면_그것을_돌려준다() {
        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
            .thenReturn(List.of(prop("CHALLENGE_EXPIRE_SECONDS", 9L, "120")));

        assertThat(service.load().get("CHALLENGE_EXPIRE_SECONDS")).isEqualTo("120");
    }

    /** 행이 없으면 만든다. 식별자의 COMPANY_IDX 는 유효 테넌트여야 한다. */
    @Test void 행이_없으면_유효_테넌트로_만든다() {
        when(repo.findById(any(CcfaSystemPropId.class))).thenReturn(Optional.empty());
        when(repo.save(any(CcfaSystemProp.class))).thenAnswer(i -> i.getArgument(0));

        service.save(Map.of("SMTP_HOST", "10.0.0.1"));

        ArgumentCaptor<CcfaSystemProp> captor = ArgumentCaptor.forClass(CcfaSystemProp.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getId().getPropKey()).isEqualTo("SMTP_HOST");
        assertThat(captor.getValue().getId().getCompanyIdx()).isEqualTo(9L);
        assertThat(captor.getValue().getPropValue()).isEqualTo("10.0.0.1");
    }

    /** 행이 있으면 값만 갱신한다. 식별자를 새로 만들면 다른 행이 생긴다. */
    @Test void 행이_있으면_값만_갱신한다() {
        CcfaSystemProp existing = prop("SMTP_HOST", 9L, "old");
        when(repo.findById(new CcfaSystemPropId("SMTP_HOST", 9L))).thenReturn(Optional.of(existing));
        when(repo.save(any(CcfaSystemProp.class))).thenAnswer(i -> i.getArgument(0));

        service.save(Map.of("SMTP_HOST", "10.0.0.2"));

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

        service.save(Map.of("SMTP_HOST", "10.0.0.1"));

        verify(audit).log(org.mockito.ArgumentMatchers.eq(AuditType.UPDATE), any(String.class));
    }

    /** 체크박스는 CSV 한 줄로 저장되고 같은 형태로 읽힌다. */
    @Test void 체크박스_CSV_왕복이_일치한다() {
        when(repo.findById(any(CcfaSystemPropId.class))).thenReturn(Optional.empty());
        when(repo.save(any(CcfaSystemProp.class))).thenAnswer(i -> i.getArgument(0));

        service.save(Map.of("AUTH_RESPONSE_OPTIONS", "PKCS1,PUBLIC_KEY"));

        ArgumentCaptor<CcfaSystemProp> captor = ArgumentCaptor.forClass(CcfaSystemProp.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getPropValue()).isEqualTo("PKCS1,PUBLIC_KEY");

        when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
            .thenReturn(List.of(prop("AUTH_RESPONSE_OPTIONS", 9L, "PKCS1,PUBLIC_KEY")));
        assertThat(service.load().get("AUTH_RESPONSE_OPTIONS")).isEqualTo("PKCS1,PUBLIC_KEY");
    }
}
