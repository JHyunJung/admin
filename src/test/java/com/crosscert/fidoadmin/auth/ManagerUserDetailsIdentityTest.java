package com.crosscert.fidoadmin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Spring Security 의 동시 세션 제어(maximumSessions)는 principal 의 equals/hashCode 로
 * 세션을 묶는다. 이것이 없으면 로그인할 때마다 다른 사용자로 취급되어 세션 1개 제한이 무력화된다.
 */
class ManagerUserDetailsIdentityTest {

    private ManagerUserDetails of(String userId, String login) {
        return new ManagerUserDetails(1L, userId, "hash", "이름", 1L, "회사", true, true);
    }

    @Test void sameManagerIsEqualAcrossLogins() {
        ManagerUserDetails first = of("kbadmin", "ON-LINE");
        ManagerUserDetails second = of("kbadmin", "OFF-LINE");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    /**
     * DaoAuthenticationProvider 는 principal 이 CredentialsContainer 일 때만 로그인 뒤 비밀번호를 지운다.
     * 메서드만 있고 인터페이스가 없으면 세션에 저장된 principal 에 SHA-256 해시(salt 없음)가 남는다.
     */
    @Test void erasesCredentialsThroughSpringSecurityContract() {
        ManagerUserDetails user = of("kbadmin", "ON-LINE");

        assertThat(user).isInstanceOf(org.springframework.security.core.CredentialsContainer.class);
        ((org.springframework.security.core.CredentialsContainer) user).eraseCredentials();
        assertThat(user.getPassword()).isNull();
    }

    @Test void differentManagersAreNotEqual() {
        ManagerUserDetails a = new ManagerUserDetails(1L, "kbadmin", "h", "이름", 1L, "회사", true, true);
        ManagerUserDetails b = new ManagerUserDetails(2L, "superuser", "h", "이름", 0L, "회사", true, true);

        assertThat(a).isNotEqualTo(b);
    }
}
