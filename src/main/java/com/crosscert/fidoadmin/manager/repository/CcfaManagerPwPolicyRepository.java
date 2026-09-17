package com.crosscert.fidoadmin.manager.repository;

import com.crosscert.fidoadmin.manager.entity.CcfaManagerPwPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CcfaManagerPwPolicyRepository extends JpaRepository<CcfaManagerPwPolicy, Long> {
    Optional<CcfaManagerPwPolicy> findFirstByUserIdOrderByIdxDesc(String userId);
}
