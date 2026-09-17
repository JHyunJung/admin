package com.crosscert.fidoadmin.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import com.crosscert.fidoadmin.system.repository.CcfaMenuRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MenuServiceTest {

    CcfaMenuRepository repo = mock(CcfaMenuRepository.class);
    AuditLogger audit = mock(AuditLogger.class);
    MenuService service = new MenuService(repo, audit);

    @BeforeEach void loginSuper() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private CcfaMenu menu(long idx, String name) { CcfaMenu m = new CcfaMenu(); m.setIdx(idx); m.setMenuName(name); return m; }

    @Test void defaultsFilledOnCreate() {
        when(repo.save(any())).thenAnswer(inv -> { CcfaMenu m = inv.getArgument(0); m.setIdx(10L); return m; });
        CcfaMenu out = service.create(menu(0L, "새 메뉴"));
        assertThat(out.getMenuParentIdx()).isZero();
        assertThat(out.getVisible()).isEqualTo("true");
        assertThat(out.getOpenType()).isEqualTo("open");
        assertThat(out.getStatistics()).isEqualTo("N");
        assertThat(out.getReadonly()).isEqualTo("N");
        verify(audit).log(AuditType.CREATE, "CCFA_MENU CREATE 10");
    }

    @Test void explicitValuesAreKept() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CcfaMenu in = menu(0L, "하위"); in.setMenuParentIdx(1L); in.setVisible("false"); in.setReadonly("Y");
        CcfaMenu out = service.create(in);
        assertThat(out.getMenuParentIdx()).isEqualTo(1L);
        assertThat(out.getVisible()).isEqualTo("false");
        assertThat(out.getReadonly()).isEqualTo("Y");
    }

    @Test void deleteBlockedWhenChildrenExist() {
        when(repo.findById(1L)).thenReturn(Optional.of(menu(1L, "FIDO")));
        when(repo.countByMenuParentIdx(1L)).thenReturn(3L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("하위 메뉴가 3건");
        verify(repo, never()).delete(any(CcfaMenu.class));
        verify(audit, never()).log(any(), any());
    }

    @Test void deleteLeafSucceeds() {
        when(repo.findById(2L)).thenReturn(Optional.of(menu(2L, "앱 ID")));
        when(repo.countByMenuParentIdx(2L)).thenReturn(0L);
        service.delete(2L);
        verify(repo).delete(any(CcfaMenu.class));
        verify(audit).log(AuditType.DELETE, "CCFA_MENU DELETE 2");
    }

    @Test void selectListIsOrderedByRepositoryQuery() {
        when(repo.findAllByOrderByMenuParentIdxAscMenuSeqAscIdxAsc()).thenReturn(List.of(menu(1L, "FIDO"), menu(2L, "앱 ID")));
        assertThat(service.allForSelect()).extracting(CcfaMenu::getMenuName).containsExactly("FIDO", "앱 ID");
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Order.asc("menuParentIdx"), Sort.Order.asc("menuSeq"), Sort.Order.asc("idx")));
        assertThat(service.sortableProperties()).contains("idx", "menuName", "menuCode", "menuParentIdx", "menuSeq");
    }
}
