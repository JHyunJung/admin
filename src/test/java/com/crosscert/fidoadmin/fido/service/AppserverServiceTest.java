package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.system.reload.FidoConfigChanged;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.repository.AppserverRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AppserverServiceTest {

    AppserverRepository repo = mock(AppserverRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    MemberCodeGenerator memberCodes = mock(MemberCodeGenerator.class);
    AppserverService service = new AppserverService(repo, audit, tenant, events, memberCodes);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test void typeDefaultsToUseAndTimestampsSet() {
        login(0L);
        selected.select(1L); // 미선택 SUPER 는 NoTenantSelectedException; 테넌시는 이 테스트의 관심사가 아니다
        when(repo.save(any())).thenAnswer(inv -> { Appserver a = inv.getArgument(0); a.setIdx(7L); return a; });
        Appserver in = new Appserver(); in.setMemberCode("KB01"); in.setMemberId("kbsvr");

        Appserver out = service.create(in);

        assertThat(out.getType()).isEqualTo("use");
        assertThat(out.getCreatedtime()).isNotNull();
        assertThat(out.getUpdatedtime()).isEqualTo(out.getCreatedtime());
        verify(audit).log(AuditType.CREATE, "APPSERVER CREATE 7");
    }

    @Test void updateTouchesOnlyUpdatedtime() {
        login(1L);
        Appserver existing = new Appserver(); existing.setIdx(3L); existing.setCompanyIdx(1L);
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);
        existing.setCreatedtime(created); existing.setUpdatedtime(created);
        when(repo.findById(3L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(3L, a -> a.setNote("변경"));

        assertThat(existing.getCreatedtime()).isEqualTo(created);
        assertThat(existing.getUpdatedtime()).isAfter(created);
        verify(audit).log(eq(AuditType.UPDATE), startsWith("APPSERVER UPDATE 3"));
    }

    @Test void companyRoleCreateForcesOwnTenant() {
        login(1L);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Appserver in = new Appserver(); in.setMemberCode("X"); in.setMemberId("Y"); in.setCompanyIdx(999L);
        assertThat(service.create(in).getCompanyIdx()).isEqualTo(1L);
    }

    @Test void duplicateCheckIsScopedToTenantAndExcludesSelf() {
        login(1L);
        when(repo.existsByCompanyIdxAndMemberCodeAndMemberIdAndIdxNot(1L, "M01", "ID01", 5L)).thenReturn(true);
        assertThat(service.existsDuplicate("M01", "ID01", 5L)).isTrue();
    }

    @Test void duplicateCheckOnCreateUsesExistsWithoutExclusion() {
        login(1L);
        when(repo.existsByCompanyIdxAndMemberCodeAndMemberId(1L, "M01", "ID01")).thenReturn(true);
        assertThat(service.existsDuplicate("M01", "ID01", null)).isTrue();
    }

    @Test void blankValuesAreNeverDuplicates() {
        assertThat(service.existsDuplicate(null, "ID01", null)).isFalse();
        assertThat(service.existsDuplicate("M01", " ", null)).isFalse();
    }

    @Test void sortablePropertiesCoverListColumns() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "memberCode", "memberId", "createdtime");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }

    @Test void createUpdateDeletePublishReload() {
        login(1L);
        when(repo.save(any())).thenAnswer(inv -> { Appserver a = inv.getArgument(0); if (a.getIdx() == null) a.setIdx(12L); return a; });
        Appserver created = service.create(new Appserver());
        verify(events).publishEvent(new FidoConfigChanged("APPSERVER CREATE 12"));

        when(repo.findById(12L)).thenReturn(Optional.of(created));
        service.update(12L, a -> a.setMemberId("m"));
        verify(events).publishEvent(new FidoConfigChanged("APPSERVER UPDATE 12"));

        service.delete(12L);
        verify(events).publishEvent(new FidoConfigChanged("APPSERVER DELETE 12"));
    }

    @Test void createFillsGeneratedMemberCode() {
        login(1L);
        when(memberCodes.generate()).thenReturn("K7Q2M9XA4D");
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Appserver in = new Appserver(); in.setMemberId("kbsvr");

        assertThat(service.create(in).getMemberCode()).isEqualTo("K7Q2M9XA4D");
    }

    @Test void createOverwritesAnyMemberCodeFromCaller() {
        login(1L);
        when(memberCodes.generate()).thenReturn("K7Q2M9XA4D");
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Appserver in = new Appserver(); in.setMemberCode("TYPED"); in.setMemberId("kbsvr");

        assertThat(service.create(in).getMemberCode()).isEqualTo("K7Q2M9XA4D");
    }

    @Test void updateNeverGeneratesMemberCode() {
        login(1L);
        Appserver existing = new Appserver(); existing.setIdx(3L); existing.setCompanyIdx(1L); existing.setMemberCode("OLD0000001");
        when(repo.findById(3L)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(3L, a -> a.setNote("변경"));

        assertThat(existing.getMemberCode()).isEqualTo("OLD0000001");
        verify(memberCodes, never()).generate();
    }
}
