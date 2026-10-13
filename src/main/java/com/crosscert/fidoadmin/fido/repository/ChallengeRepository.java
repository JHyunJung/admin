package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.common.PartialFetch;
import com.crosscert.fidoadmin.fido.entity.Challenge;

public interface ChallengeRepository extends AdminRepository<Challenge, Long>, PartialFetch<Challenge> { // 건수 캐시 목록(PartialFetch)은 직접 상속해야 조각이 붙는다
}
