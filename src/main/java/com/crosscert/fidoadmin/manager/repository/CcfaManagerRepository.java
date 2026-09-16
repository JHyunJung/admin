package com.crosscert.fidoadmin.manager.repository;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CcfaManagerRepository extends JpaRepository<CcfaManager, Long>, JpaSpecificationExecutor<CcfaManager> {
    Optional<CcfaManager> findByUserId(String userId);
}
