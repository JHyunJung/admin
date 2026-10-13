package com.crosscert.fidoadmin.log.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.common.PartialFetch;
import com.crosscert.fidoadmin.log.entity.CcfaExceptions;

public interface CcfaExceptionsRepository extends AdminRepository<CcfaExceptions, Long>, PartialFetch<CcfaExceptions> { // CLOB 을 빼고 읽는 목록(PartialFetch)은 직접 상속해야 조각이 붙는다
}
