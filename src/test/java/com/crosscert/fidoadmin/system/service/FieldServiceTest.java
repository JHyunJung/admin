package com.crosscert.fidoadmin.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.system.entity.CcfaFields;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.repository.CcfaFieldsRepository;
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FieldServiceTest {

    CcfaFieldsRepository repo = mock(CcfaFieldsRepository.class);
    CcfaOptionRepository options = mock(CcfaOptionRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    FieldService service = new FieldService(repo, audit, options);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private CcfaFields field() {
        CcfaFields f = new CcfaFields(); f.setFieldTable("APPID"); f.setFieldName("STATUS"); f.setFieldType("select"); return f;
    }

    @Test void numericFlagsDefaultToZero() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> { CcfaFields f = inv.getArgument(0); f.setIdx(3L); return f; });
        CcfaFields out = service.create(field());
        assertThat(out.getPk()).isZero();
        assertThat(out.getFk()).isZero();
        assertThat(out.getEditable()).isZero();
        assertThat(out.getOptionIdx()).isNull();
        verify(audit).log(AuditType.CREATE, "CCFA_FIELDS CREATE 3");
    }

    @Test void explicitFlagsAreKept() {
        login(0L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaFields in = field(); in.setPk(1L); in.setEditable(1L); in.setOptionIdx(1L);
        CcfaFields out = service.create(in);
        assertThat(out.getPk()).isEqualTo(1L);
        assertThat(out.getEditable()).isEqualTo(1L);
        assertThat(out.getOptionIdx()).isEqualTo(1L);
    }

    @Test void optionsComeFromOptionRepositoryInIdxOrder() {
        login(0L);
        CcfaOption o = new CcfaOption(); o.setIdx(1L); o.setOptionName("STATUS");
        when(options.findAll(Sort.by("idx"))).thenReturn(List.of(o));
        assertThat(service.options()).extracting(CcfaOption::getOptionName).containsExactly("STATUS");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Order.asc("fieldTable"), Sort.Order.asc("idx")));
        assertThat(service.sortableProperties()).contains("idx", "fieldTable", "fieldName");
    }

    @Test void companyRoleIsDenied() {
        login(1L);
        assertThatThrownBy(() -> service.options()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(field())).isInstanceOf(AccessDeniedException.class);
    }
}
