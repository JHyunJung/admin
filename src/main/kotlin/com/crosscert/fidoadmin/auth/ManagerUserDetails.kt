package com.crosscert.fidoadmin.auth

import java.util.Objects
import org.springframework.security.core.CredentialsContainer
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * [CredentialsContainer] 를 구현해야 DaoAuthenticationProvider 가 로그인 직후
 * [eraseCredentials] 를 부른다. 메서드만 있으면 호출되지 않아 세션에 저장된 principal 에
 * 비밀번호 해시(salt 없는 SHA-256 — 그대로 로그인에 쓸 수 있는 값)가 남는다.
 */
class ManagerUserDetails(
    val idx: Long?,
    val userId: String?,
    password: String?,
    val userNm: String?,
    companyIdx: Long?,
    val companyName: String?,
    private val enabled: Boolean,
    private val accountNonLocked: Boolean,
) : UserDetails, CredentialsContainer {

    private var password: String? = password

    // null 을 0 으로 바꾸면 ROLE_SUPER 가 되어 권한이 상승한다.
    // CCFA_MANAGER.COMPANY_IDX 는 NOT NULL 이므로 null 은 비정상 상태로 보고 거부한다.
    val companyIdx: Long = companyIdx
        ?: throw IllegalArgumentException("COMPANY_IDX 가 없는 계정으로는 로그인할 수 없습니다: $userId")

    val isSuper: Boolean get() = companyIdx == 0L

    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority(if (isSuper) ROLE_SUPER else ROLE_COMPANY))
    override fun getPassword(): String? = password
    override fun getUsername(): String? = userId
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = accountNonLocked
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = enabled
    override fun eraseCredentials() { this.password = null }

    /**
     * 동시 세션 제어(maximumSessions)는 SessionRegistry 가 principal 의 equals/hashCode 로
     * 세션을 묶는다. 이것이 없으면 로그인할 때마다 다른 사용자로 취급되어 세션 1개 제한이 무력화된다.
     * 비밀번호·잠금 상태 같은 가변 값은 제외하고 불변 식별자만 사용한다.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ManagerUserDetails) return false
        return Objects.equals(idx, other.idx) && Objects.equals(userId, other.userId)
    }

    override fun hashCode(): Int = Objects.hash(idx, userId)

    companion object {
        const val ROLE_SUPER: String = "ROLE_SUPER"
        const val ROLE_COMPANY: String = "ROLE_COMPANY"
    }
}
