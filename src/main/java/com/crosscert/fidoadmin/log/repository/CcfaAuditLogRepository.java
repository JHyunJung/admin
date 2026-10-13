package com.crosscert.fidoadmin.log.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.common.PartialFetch;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;

public interface CcfaAuditLogRepository extends AdminRepository<CcfaAuditLog, Long>, PartialFetch<CcfaAuditLog> { // 건수 캐시 목록(PartialFetch)은 직접 상속해야 조각이 붙는다
}
