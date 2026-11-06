package com.crosscert.fidoadmin.manager.repository

import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository

interface CcfaManagerPwPolicyRepository : JpaRepository<CcfaManagerPwPolicy, Long> {
    fun findFirstByUserIdOrderByIdxDesc(userId: String?): Optional<CcfaManagerPwPolicy>
}
