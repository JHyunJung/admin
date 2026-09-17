package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class ManagerUserDetailsServiceTest {

    CcfaManagerRepository managers = mock(CcfaManagerRepository.class);
    CcfaManagerPwPolicyRepository policies = mock(CcfaManagerPwPolicyRepository.class);
    CcfaCompanyRepository companies = mock(CcfaCompanyRepository.class);
    ManagerUserDetailsService service = new ManagerUserDetailsService(managers, policies, companies);

    private CcfaManager manager(String status, Long companyIdx) {
        CcfaManager m = new CcfaManager();
        m.setIdx(1L); m.setUserId("kbadmin"); m.setUserPw("hash"); m.setUserNm("KB운영자");
        m.setStatus(status); m.setCompanyIdx(companyIdx);
        return m;
    }

    /**
     * COMPANY_IDX 가 null 인 계정은 로그인이 거부되어야 한다.
     * null 을 0 으로 치환하면 isSuper() 가 참이 되어 슈퍼 관리자로 승격된다.
     */
    @Test void nullCompanyIdxIsRejectedInsteadOfBecomingSuper() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", null)));
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("kbadmin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("COMPANY_IDX");
    }

    /** COMPANY_IDX 가 null 이면 회사명 조회로 DB 를 찌르지 않는다. */
    @Test void nullCompanyIdxDoesNotQueryCompany() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", null)));
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("kbadmin"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(companies, never()).findById(any());
    }

    @Test void loadsActiveManagerWithCompanyRole() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", 1L)));
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty());
        CcfaCompany c = new CcfaCompany(); c.setIdx(1L); c.setCompanyName("KB국민은행");
        when(companies.findById(1L)).thenReturn(Optional.of(c));

        ManagerUserDetails u = (ManagerUserDetails) service.loadUserByUsername("kbadmin");

        assertThat(u.isEnabled()).isTrue();
        assertThat(u.isAccountNonLocked()).isTrue();
        assertThat(u.getCompanyName()).isEqualTo("KB국민은행");
        assertThat(u.getAuthorities()).extracting("authority").containsExactly("ROLE_COMPANY");
    }

    @Test void inactiveStatusIsDisabled() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("비활성", 0L)));
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty());
        when(companies.findById(0L)).thenReturn(Optional.empty());

        ManagerUserDetails u = (ManagerUserDetails) service.loadUserByUsername("kbadmin");
        assertThat(u.isEnabled()).isFalse();
        assertThat(u.isSuper()).isTrue();
        assertThat(u.getCompanyName()).isEqualTo("전역");
    }

    @Test void lockedPolicyLocksAccount() {
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(manager("활성", 1L)));
        CcfaManagerPwPolicy p = new CcfaManagerPwPolicy(); p.setAccountLock("Y");
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.of(p));
        when(companies.findById(1L)).thenReturn(Optional.empty());

        ManagerUserDetails u = (ManagerUserDetails) service.loadUserByUsername("kbadmin");
        assertThat(u.isAccountNonLocked()).isFalse();
    }

    @Test void unknownUserThrows() {
        when(managers.findByUserId("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("x")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test void nullNameFallsBackToUserId() {
        CcfaManager m = manager("활성", 1L); m.setUserNm(null);
        when(managers.findByUserId("kbadmin")).thenReturn(Optional.of(m));
        when(policies.findFirstByUserIdOrderByIdxDesc("kbadmin")).thenReturn(Optional.empty());
        when(companies.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.loadUserByUsername("kbadmin")).extracting("userNm").isEqualTo("kbadmin");
    }
}
