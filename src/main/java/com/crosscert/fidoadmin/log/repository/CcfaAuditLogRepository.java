package com.crosscert.fidoadmin.log.repository;

import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CcfaAuditLogRepository extends JpaRepository<CcfaAuditLog, Long>, JpaSpecificationExecutor<CcfaAuditLog> {
}
