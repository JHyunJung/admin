package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.common.PartialFetch;
import com.crosscert.fidoadmin.fido.entity.Criteria;

public interface CriteriaRepository extends AdminRepository<Criteria, Long>, PartialFetch<Criteria> { // CLOB 을 빼고 읽는 목록(PartialFetch)은 직접 상속해야 조각이 붙는다
    boolean existsByAaid(String aaid);
}
