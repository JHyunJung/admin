package com.crosscert.fidoadmin.auth;

import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class ManagerUserDetails implements UserDetails {

    public static final String ROLE_SUPER = "ROLE_SUPER";
    public static final String ROLE_COMPANY = "ROLE_COMPANY";

    private final Long idx;
    private final String userId;
    private final String userNm;
    private final Long companyIdx;
    private final String companyName;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private String password;

    public ManagerUserDetails(Long idx, String userId, String password, String userNm, Long companyIdx,
                              String companyName, boolean enabled, boolean accountNonLocked) {
        this.idx = idx;
        this.userId = userId;
        this.password = password;
        this.userNm = userNm;
        // null 을 0 으로 바꾸면 ROLE_SUPER 가 되어 권한이 상승한다.
        // CCFA_MANAGER.COMPANY_IDX 는 NOT NULL 이므로 null 은 비정상 상태로 보고 거부한다.
        if (companyIdx == null) {
            throw new IllegalArgumentException("COMPANY_IDX 가 없는 계정으로는 로그인할 수 없습니다: " + userId);
        }
        this.companyIdx = companyIdx;
        this.companyName = companyName;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
    }

    public boolean isSuper() { return companyIdx == 0L; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(isSuper() ? ROLE_SUPER : ROLE_COMPANY));
    }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return userId; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
    public void eraseCredentials() { this.password = null; }
}
