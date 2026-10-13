package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido.entity.Challenge;
import com.crosscert.fidoadmin.fido.repository.ChallengeRepository;
import com.crosscert.fidoadmin.fido.web.ChallengeSearchForm;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ChallengeQueryServiceTest {

    ChallengeRepository repo = mock(ChallengeRepository.class);
    ChallengeQueryService service = new ChallengeQueryService(repo, mock(AuditLogger.class), new TenantContext(new SelectedTenant()));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "u", null, "n", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    /** 시퀀스 PK 역순. PK 인덱스를 거꾸로 읽어 첫 페이지만 가져온다(생성 시각 정렬은 전체 정렬이 필요하다). */
    @Test void defaultSortIsIdxDescending() {
        assertThat(service.defaultSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idx"));
    }

    @Test void sortablePropertiesIncludeCreatetime() {
        assertThat(service.sortableProperties()).containsExactlyInAnyOrder("idx", "createtime");
    }

    @Test void idOfIsIdx() {
        Challenge c = new Challenge(); c.setIdx(42L);
        assertThat(service.idOf(c)).isEqualTo("42");
    }

    /** COMPANY 로그인으로 검색하면 Specification 이 만들어져 repository 에 전달된다(테넌트 필터는 기반 테스트가 검증). */
    @Test void searchPassesSpecificationToRepository() {
        login(1L);
        when(repo.findContentExcluding(any(), any(Specification.class), any(), any())).thenReturn(List.of());
        ChallengeSearchForm f = new ChallengeSearchForm();
        f.setUserid("user001");
        service.search(f, PageRequest.of(0, 20, service.defaultSort()));
        // 건수 캐시를 쓰므로 본문은 findContentExcluding 으로 읽는다.
        verify(repo).findContentExcluding(any(), any(Specification.class), any(), any());
    }
}
