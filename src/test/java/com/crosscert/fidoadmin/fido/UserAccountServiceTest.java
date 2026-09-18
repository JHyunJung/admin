package com.crosscert.fidoadmin.fido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.fido.repository.UserinfoRepository;
import com.crosscert.fidoadmin.fido.service.UserAccountRow;
import com.crosscert.fidoadmin.fido.service.UserAccountService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class UserAccountServiceTest {

    UserinfoRepository repo = mock(UserinfoRepository.class);
    SelectedTenant selected = new SelectedTenant();
    TenantContext tenant = new TenantContext(selected);
    UserAccountService service = new UserAccountService(repo, tenant);

    @BeforeEach void loginSuperSelecting() {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        selected.select(9L);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private UserAccountRow row(String userid, long devices, long active) {
        return new UserAccountRow(userid, "kbstar", devices, active, LocalDateTime.now());
    }

    /**
     * 이 서비스는 CrudService 를 거치지 않으므로 테넌트 경계가 여기 말고는 없다.
     * 유효 테넌트가 질의로 전달되지 않으면 다른 고객사의 사용자가 보인다.
     */
    @Test void 목록은_유효_테넌트로만_조회한다() {
        when(repo.findUserAccounts(any(), any(), any(), any())).thenReturn(List.of());
        when(repo.countUserAccounts(any(), any(), any())).thenReturn(0L);

        service.search(null, null, PageRequest.of(0, 20));

        verify(repo).findUserAccounts(eq(9L), eq(null), eq(null), any());
        verify(repo).countUserAccounts(eq(9L), eq(null), eq(null));
    }

    /** 기기 목록도 같은 경계를 지킨다. */
    @Test void 기기_목록도_유효_테넌트로만_조회한다() {
        when(repo.findByCompanyIdxAndUseridAndServicenameOrderByRegtimeDesc(any(), any(), any()))
            .thenReturn(List.of());

        service.credentialsOf("user001", "kbstar");

        verify(repo).findByCompanyIdxAndUseridAndServicenameOrderByRegtimeDesc(9L, "user001", "kbstar");
    }

    /** 테넌트를 바꾸면 그 값으로 조회한다. 선택이 질의에 실제로 반영되는지 본다. */
    @Test void 테넌트를_바꾸면_바뀐_값으로_조회한다() {
        when(repo.findUserAccounts(any(), any(), any(), any())).thenReturn(List.of());
        when(repo.countUserAccounts(any(), any(), any())).thenReturn(0L);

        selected.select(2L);
        service.search(null, null, PageRequest.of(0, 20));

        verify(repo).findUserAccounts(eq(2L), any(), any(), any());
    }

    /** 빈 검색어는 조건을 걸지 않는다. 빈 문자열을 그대로 넘기면 LIKE '%%' 가 되어 의도가 흐려진다. */
    @Test void 빈_검색어는_null_로_넘어간다() {
        when(repo.findUserAccounts(any(), any(), any(), any())).thenReturn(List.of());
        when(repo.countUserAccounts(any(), any(), any())).thenReturn(0L);

        service.search("  ", "", PageRequest.of(0, 20));

        verify(repo).findUserAccounts(eq(9L), eq(null), eq(null), any());
    }

    /** 검색어가 있으면 그대로 넘긴다. */
    @Test void 검색어는_그대로_전달된다() {
        when(repo.findUserAccounts(any(), any(), any(), any())).thenReturn(List.of());
        when(repo.countUserAccounts(any(), any(), any())).thenReturn(0L);

        service.search("user0", "kb", PageRequest.of(0, 20));

        verify(repo).findUserAccounts(eq(9L), eq("user0"), eq("kb"), any());
    }

    /** 총건수는 그룹 개수여야 한다 — 행 개수를 쓰면 쪽수가 부풀어 빈 페이지가 생긴다. */
    @Test void 총건수는_그룹_개수를_쓴다() {
        when(repo.findUserAccounts(any(), any(), any(), any()))
            .thenReturn(List.of(row("user001", 3, 2), row("user002", 1, 1)));
        when(repo.countUserAccounts(any(), any(), any())).thenReturn(2L);

        var page = service.search(null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }

    /** 집계값이 화면까지 그대로 온다. */
    @Test void 기기수와_정상수를_그대로_돌려준다() {
        when(repo.findUserAccounts(any(), any(), any(), any())).thenReturn(List.of(row("user001", 3, 2)));
        when(repo.countUserAccounts(any(), any(), any())).thenReturn(1L);

        var first = service.search(null, null, PageRequest.of(0, 20)).getContent().get(0);

        assertThat(first.userid()).isEqualTo("user001");
        assertThat(first.deviceCount()).isEqualTo(3);
        assertThat(first.activeCount()).isEqualTo(2);
    }
}
