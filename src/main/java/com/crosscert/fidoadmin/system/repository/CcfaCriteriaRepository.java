package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.common.PartialFetch;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;

public interface CcfaCriteriaRepository extends AdminRepository<CcfaCriteria, Long>, PartialFetch<CcfaCriteria> { // CLOB 을 빼고 읽는 목록(PartialFetch)은 직접 상속해야 조각이 붙는다
}
