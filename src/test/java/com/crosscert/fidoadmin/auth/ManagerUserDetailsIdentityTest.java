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

    @Test void differentManagersAreNotEqual() {
        ManagerUserDetails a = new ManagerUserDetails(1L, "kbadmin", "h", "이름", 1L, "회사", true, true);
        ManagerUserDetails b = new ManagerUserDetails(2L, "superuser", "h", "이름", 0L, "회사", true, true);

        assertThat(a).isNotEqualTo(b);
    }
}
