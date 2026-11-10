package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.common.isNullOrJavaBlank
import com.crosscert.fidoadmin.common.ManagerStatus
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository
import java.time.LocalDateTime
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ManagerUserDetailsService(
    private val managers: CcfaManagerRepository,
    private val policies: CcfaManagerPwPolicyRepository,
    private val companies: CcfaCompanyRepository,
    private val attempts: LoginAttemptService,
) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String?): UserDetails {
        val m = managers.findByUserId(username)
            .orElseThrow { UsernameNotFoundException("운영자 없음: $username") }
        // 잠금 판정은 LoginAttemptService 에 모아 둔다 — 잠근 지 N분이 지나면 풀린 것으로 본다.
        // 여기서는 읽기만 하고, 실제로 푸는 것은 로그인 성공(onSuccess) 쪽이다.
        val now = LocalDateTime.now()
        val locked: Boolean = policies.findFirstByUserIdOrderByIdxDesc(username)
            .map { p -> attempts.isLocked(p, now) }.orElse(false)
        // COMPANY_IDX 가 null 이어도 0 으로 치환하지 않는다. 0 은 SUPER 이므로 치환은 권한 상승이다.
        // null 은 ManagerUserDetails 생성자가 거부한다.
        val companyIdx = m.companyIdx
        val companyName: String? = if (companyIdx == null) null
            else companies.findById(companyIdx).map { it.companyName }
                .orElse(if (companyIdx == 0L) "전역" else "고객사 $companyIdx")
        val name = if (m.userNm.isNullOrJavaBlank()) m.userId else m.userNm
        return ManagerUserDetails(m.idx, m.userId, m.userPw, name, companyIdx, companyName,
            ManagerStatus.ACTIVE == m.status, !locked)
    }
}
